# BlockSets
This repository contains a prototype implementation of BlockSets.
It consists of two parts:
- `ILP`: a Java project that parses input, computes a compact layout, and saves this to a file.
- `Visualization`: a plain-Javascript website that parses a solution file computed by the Java project and renders it on a canvas element or to an SVG.

The `Orchestrator.java` file is the entrypoint for the ILP.
Compile and run it to produce a layout.
As input the program expects a JSON file; see the `example_data` folder for examples, which also show the format the program expects.
To render the result, run a local http server to host the webpage in the `Visualization` directory, and open the solution files (see `example_solutions` for examples) in the web page in the browser.

## Dependencies
- Gurobi (we used version 12.0.2, see [the Gurobi website for installation instructions](https://www.gurobi.com/documentation/quickstart.html))
- JSON parsing library: org.json (https://github.com/stleary/JSON-java)

## Note
The implementation currently does not support arbitrary rectilinear polygons. For that, refer to [the MosaicSets implementation](https://gitlab.igg.uni-bonn.de/geoinfo/mosaicsets).
Currently, elements can only be rendered as text; rendering them as images is currently not supported.