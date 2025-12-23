# PDF Deduplication Project

## Overview
This project is designed to identify and remove duplicate PDF files from a given directory. It ensures that only unique PDF files are retained, which can help save storage space and improve file organization.

## Features
- Scans a directory for PDF files.
- Compares the content of PDF files to detect duplicates.
- Removes or flags duplicate files while preserving unique ones.

## How It Works
1. The program reads all PDF files from the specified directory.
2. It uses a content-based comparison to identify duplicates. This ensures that even if the file names are different, duplicates are still detected.
3. Duplicate files are either deleted or flagged based on the user's preference.

## Prerequisites
- Java Development Kit (JDK) 11 or higher.
- Gradle build tool.

## How to Run
1. Clone the repository:
   ```bash
   git clone git@github.com:sbkohel/pdf_dedupe.git
   ```
2. Navigate to the project directory:
   ```bash
   cd pdf_dedupe
   ```
3. Build the project using Gradle:
   ```bash
   ./gradlew build
   ```
4. Run the application:
   ```bash
   ./gradlew run
   ```

## Directory Structure
- `src/main/java`: Contains the main application code.
- `src/test/java`: Contains test cases for the application.
- `distinct_files/`: Example directory with sample PDF files for testing.

## Contribution
Feel free to fork the repository and submit pull requests. For major changes, please open an issue first to discuss what you would like to change.

## License
This project is licensed under the MIT License. See the LICENSE file for details.
