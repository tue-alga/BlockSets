# BlockSets: A Structured Visualization for Sets with Large Elements
This repository contains a prototype implementation of BlockSets.
It consists of two parts:
- `ILP`: a Java project that parses input, computes a compact layout, and saves this to a file.
- `Visualization`: a plain-JavaScript website that parses a solution file computed by the Java project and renders it on a canvas element or to an SVG.

The `Orchestrator.java` file is the entrypoint for the ILP.
Compile and run it to produce a layout.
As input the program expects a JSON file; see the `example_data` folder for examples, which also show the format the program expects.
To render the result, run a local HTTP server to host the webpage in the `Visualization` directory, and open the solution files (see `example_solutions` for examples) in the web page in the browser.

## Experiments
We ran experiments to evaluate the effect of the different shapes on the solution, and to compare BlockSets to MosaicSets and RectEuler.
The data, results, and analysis are all available in the repository.
The `experiment_data` folder contains the input data, the `experiment_solutions` folder contains the solutions, and the `experiment_analysis` folder contains the gathered statistics (`experiment_data.csv`) and the Jupyter notebooks used to analyze them.
The Java file `ILP/src/Experiments.java` was used to gather the results.

## Integration with RectEuler and MosaicSets
We integrated the RectEuler splitting algorithm and the MosaicSets layout ILP into BlockSets.
See the corresponding papers and code for more details on their techniques.

> P. Rottmann, M. Wallinger, A. Bonerath, S. Gedicke, M. Nöllenburg, and J. Haunert.<br>
> Mosaicsets: Embedding set systems into grid graphs.<br>
> IEEE Transactions on Visualization and Computer Graphics, 29(1):875–885, 2023.<br>
> [doi: 10.1109/TVCG.2022.3209485](https://doi.org/10.1109/TVCG.2022.3209485)

Code: https://gitlab.igg.uni-bonn.de/geoinfo/mosaicsets

> P. Paetzold, R. Kehlbeck, H. Strobelt, Y. Xue, S. Storandt, and O. Deussen.<br>
> RectEuler: Visualizing intersecting sets using rectangles.<br>
> Computer Graphics Forum, 42(3):87–98, 2023.<br>
> [doi: 10.1111/CGF.14814](https://doi.org/10.1111/CGF.14814)

Code: https://github.com/PatrickPaetzold/RectEuler

## Dependencies
BlockSets depends on:
- Gurobi (we used version 12.0.2, see [the Gurobi website for installation instructions](https://www.gurobi.com/documentation/quickstart.html))
- JSON parsing library: org.json (https://github.com/stleary/JSON-java)

The MosaicSets integration depends additionally on:
- batik (v1.14)
- geotools (v24.0)
- jgrapht-core (v1.5.0)
- JTS (v1.17.1)

To run the RectEuler split algorithm, you need to set up a venv Python environment in `ILP/src/split/RectEuler-Split` called `.venv`.
We used Python version 3.11. Install the packages listed in `requirements.txt` in the virtual environment.

## Note
Currently, elements can be rendered only as text; rendering them as images is currently not supported.