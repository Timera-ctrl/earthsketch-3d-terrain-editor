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

## Getting Started

### Prerequisites

Before running EarthSketch, ensure you have:

- **Java Development Kit (JDK) 21**
- **Git** for cloning the repository
- A desktop environment with graphics support

The project uses Gradle 9.6.1 through the included Gradle Wrapper, so a separate Gradle installation is not required.

### 1. Clone the Repository

Open a terminal and run:

```bash
git clone https://github.com/Timera-ctrl/earthsketch-3d-terrain-editor.git
cd earthsketch-3d-terrain-editor
```

**Note:** The repository must be publicly accessible, or you must have permission to clone it.

### 2. Build the Project

On Linux, macOS, or WSL:

```bash
bash ./gradlew clean build --console=plain --info
```

On Windows (Command Prompt or PowerShell):

```powershell
.\gradlew.bat clean build --console=plain --info
```

This compiles the Java source code, executes the configured tests, and generates the application build artifacts.

### 3. Run EarthSketch

On Linux, macOS, or WSL:

```bash
bash ./gradlew lwjgl3:run --console=plain
```

On Windows:

```powershell
.\gradlew.bat lwjgl3:run --console=plain
```

This launches the EarthSketch desktop application using the libGDX LWJGL3 backend.

**Note:** A graphical desktop environment is required. Running through WSL may require WSLg or another working graphical display configuration.

### 4. Generate an Executable JAR

To package the desktop application:

```bash
bash ./gradlew lwjgl3:jar --console=plain
```

The generated JAR is located at:

```text
lwjgl3/build/libs/EarthSketch-1.0.0.jar
```

You can launch the generated JAR using:

```bash
java -jar lwjgl3/build/libs/EarthSketch-1.0.0.jar
```

The generated JAR is excluded from Git version control because it is a build artifact.

### 5. Sample Terrain Data

Example terrain files are provided in the `assets/DATA/` directory:

- `D1-256/`
- `D2-512/`
- `D3-1024/`
- `D4-1024/`

These directories contain `.elv` elevation datasets that can be used to explore EarthSketch's terrain loading, visualisation, and editing functionality.
- Tana Gwatikunda
- Mlungisi Sibeko

## Project Context

EarthSketch investigates interactive soil-data authoring for digital landscapes, with applications in computer graphics, virtual environments, vegetation modelling, and terrain simulation.
