import json
import logging
import sys
from pathlib import Path
import time

import numpy as np
from sklearn_extra.cluster import KMedoids


logging.basicConfig(level=logging.INFO)


def cluster_split_from_vectors(statement_ids, entity_ids, vectors,
                               number_of_sublayouts=2,
                               random_state=0,
                               num_try=0,
                               min_cluster_size=5):
    """
    Mirrors the RectEuler clusterSplit retry behavior.

    Input:
        statement_ids: list[int]
        entity_ids: list[int]
        vectors: list[list[int]] or list[list[bool]]
            One vector per statement, columns correspond to entity_ids.

    Output:
        dict with:
            - statement_ids
            - entity_ids
            - assignments
            - clusters
            - medoid_indices
            - medoid_statement_ids
            - random_state_used
    """
    if len(statement_ids) != len(vectors):
        raise ValueError("Length of statement_ids must equal number of vectors")

    if len(statement_ids) == 0:
        raise ValueError("No statements to cluster")

    if number_of_sublayouts <= 0:
        raise ValueError("number_of_sublayouts must be >= 1")

    if number_of_sublayouts > len(statement_ids):
        raise ValueError("number_of_sublayouts cannot exceed number of statements")

    start_time = time.perf_counter()
    # RectEuler uses vectors of 0/1 with metric='jaccard'
    to_cluster = np.asarray(vectors, dtype=bool)

    kmedoids = KMedoids(
        n_clusters=number_of_sublayouts,
        random_state=random_state,
        init="k-medoids++",
        metric="jaccard"
    )

    clusters = kmedoids.fit_predict(to_cluster)

    # Build cluster -> statement IDs mapping
    cluster_statement_ids = [[] for _ in range(number_of_sublayouts)]
    for i, c in enumerate(clusters):
        cluster_statement_ids[int(c)].append(statement_ids[i])

    # Retry logic, matching RectEuler's:
    # if any cluster is too small, retry with seed * 11, up to 3 retries
    if num_try <= 3:
        for cluster_list in cluster_statement_ids:
            if len(cluster_list) <= min_cluster_size:
                new_seed = random_state * 11
                logging.info(f"Split again with new seed {new_seed}")
                return cluster_split_from_vectors(
                    statement_ids=statement_ids,
                    entity_ids=entity_ids,
                    vectors=vectors,
                    number_of_sublayouts=number_of_sublayouts,
                    random_state=new_seed,
                    num_try=num_try + 1,
                    min_cluster_size=min_cluster_size
                )

    medoid_indices = [int(x) for x in kmedoids.medoid_indices_]
    medoid_statement_ids = [statement_ids[i] for i in medoid_indices]

    clustering_time_seconds = time.perf_counter() - start_time

    return {
        "statement_ids": statement_ids,
        "entity_ids": entity_ids,
        "assignments": [int(x) for x in clusters.tolist()],
        "clusters": cluster_statement_ids,
        "medoid_indices": medoid_indices,
        "medoid_statement_ids": medoid_statement_ids,
        "random_state_used": random_state,
        "clustering_time_seconds": clustering_time_seconds
    }


def run_clustering(input_file, output_file,
                   number_of_sublayouts=2,
                   random_state=0,
                   min_cluster_size=5):
    input_path = Path(input_file)
    output_path = Path(output_file)

    with input_path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    statement_ids = data["statement_ids"]
    entity_ids = data["entity_ids"]
    vectors = data["vectors"]

    
    result = cluster_split_from_vectors(
        statement_ids=statement_ids,
        entity_ids=entity_ids,
        vectors=vectors,
        number_of_sublayouts=number_of_sublayouts,
        random_state=random_state,
        num_try=0,
        min_cluster_size=min_cluster_size
    )
    

    with output_path.open("w", encoding="utf-8") as f:
        json.dump(result, f, indent=2)

    logging.info(f"Wrote clustering result to {output_path}")


if __name__ == "__main__":
    """
    Usage:
        python cluster_split.py input.json output.json 2 0 5

    Arguments:
        1. input json path
        2. output json path
        3. number_of_sublayouts (optional, default 2)
        4. random_state (optional, default 0)
        5. min_cluster_size (optional, default 5)
    """
    if len(sys.argv) < 3:
        print("Usage: python cluster_split.py <input.json> <output.json> [k] [random_state] [min_cluster_size]")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]
    k = int(sys.argv[3]) if len(sys.argv) >= 4 else 2
    random_state = int(sys.argv[4]) if len(sys.argv) >= 5 else 0
    min_cluster_size = int(sys.argv[5]) if len(sys.argv) >= 6 else 5

    run_clustering(
        input_file=input_file,
        output_file=output_file,
        number_of_sublayouts=k,
        random_state=random_state,
        min_cluster_size=min_cluster_size
    )