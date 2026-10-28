# EarthSketch — Interactive 3D Terrain Editor

A Java-based 3D terrain authoring application developed using libGDX as part of a final-year Computer Science Capstone Project at the University of Cape Town.

EarthSketch enables users to load digital elevation models, explore 3D landscapes, and interactively edit soil composition and depth using terrain painting tools.

## Project Overview

The project focuses on creating an interactive environment for authoring soil properties across digital terrains.

Users can load elevation data, navigate a 3D landscape, inspect terrain properties, and paint soil composition and depth directly onto the terrain.

## Features

- Load digital elevation models (.elv files)
- Generate and render 3D terrain meshes
- Orbit and zoom around terrains
- Select and inspect terrain points using ray picking
- Paint soil properties with interactive circular brushes
- Adjust brush parameters through the graphical interface
- Visualise sand, silt, and clay proportions using colour mapping
- Load and save soil data

## Technical Implementation

- **Terrain Rendering:** Converts elevation grids into 3D terrain meshes using libGDX.
- **Ray Picking:** Maps screen coordinates to terrain positions for inspection and painting.
- **Soil Painting:** Modifies soil composition and depth using continuous brush strokes.
- **Soil Visualisation:** Uses colour mapping to represent sand, silt, and clay proportions.
- **File Management:** Supports loading elevation models and saving edited soil properties.
- **Graphical Interface:** Provides interactive controls for terrain navigation and editing.

## My Contribution

This project was developed collaboratively by a three-person team.

My contributions included:

- Implementing 3D ray picking and terrain-point inspection.
- Developing soil painting functionality, including continuous brush strokes.
- Implementing soil composition colour rendering and soil-data loading/saving.
- Contributing to terrain mesh refactoring, camera controls, and GUI integration.
- Coordinating development activities and maintaining project documentation and backlog records.
- Integrating independently developed components and resolving compatibility issues.

## Technologies

- Java
- libGDX
- Gradle
- Git and GitLab
- 3D Computer Graphics
- Ray Casting
- Terrain Modelling

## Team Project

Developed at the University of Cape Town in 2026 under the supervision of Prof. James Gain.

**Team members:**
- Timera Govender
- Tana Gwatikunda
- Mlungisi Sibeko

## Project Context

EarthSketch investigates interactive soil-data authoring for digital landscapes, with applications in computer graphics, virtual environments, vegetation modelling, and terrain simulation.
