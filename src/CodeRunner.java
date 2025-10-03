import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeRunner {

    public static String runCode(String language, String code) {
        Path tempDirPath = null;
        try {
            // 1. Create a temporary, isolated directory for compilation/execution
            tempDirPath = Files.createTempDirectory("code_exec_" + UUID.randomUUID().toString());
            String output;
            
            // --- START CRITICAL STRING DE-ESCAPING & CLEANUP ---
            // The Java String 'code' received from the JSON payload contains escaped characters.
            String finalCode = code;

            // 1. De-escape escaped quotes (e.g., in a string literal like "Hello \"World\"")
            // This converts the two characters: backslash-quote, into a single quote character.
            finalCode = finalCode.replace("\\\"", "\"");

            // 2. Convert escaped newlines/tabs (e.g., \n, \t) into actual characters.
            // These characters arrive as the two-character sequences \n and \t in the Java String variable.
            finalCode = finalCode.replaceAll("\\\\(\\r?\\n)", "$1");
            finalCode = finalCode.replaceAll("\\\\r\\\\n", "\n"); 
            finalCode = finalCode.replaceAll("\\\\n", "\n");       
            finalCode = finalCode.replaceAll("\\\\t", "\t");       
            
            // 3. FINAL AGGRESSIVE CLEANUP: FIXES "illegal character: '\'"
            // This is the specific fix for stray backslashes remaining at the end of a line.
            // It finds a literal backslash (escaped four times for regex and string literal), 
            // followed by optional spaces, and then a newline character (\n).
            // It replaces the backslash and spaces with *just* the newline, eliminating the illegal character.
            // NOTE: The \\s* accounts for any possible space characters that might precede the newline.
            finalCode = finalCode.replaceAll("\\\\ *\\n", "\n");

            // Final check for residual backslashes at the end of the entire string
            finalCode = finalCode.replaceAll("\\\\$", "");
            
            // --- END CRITICAL STRING DE-ESCAPING & CLEANUP ---
            
            // --- Execute based on language ---

            if (language.equalsIgnoreCase("python")) {
                Path tempFile = tempDirPath.resolve("tempCode.py");
                Files.writeString(tempFile, finalCode);

                String pythonCommand = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
                output = executeProcess(tempDirPath, pythonCommand, tempFile.getFileName().toString());

            } else if (language.equalsIgnoreCase("java")) {
                // Regex to find and replace the public class name to avoid conflicts
                Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)", Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(finalCode);
                String className = "TempProgram_" + UUID.randomUUID().toString().replace("-", "");
                
                if (matcher.find()) {
                    // Update the class name in the code
                    finalCode = matcher.replaceAll("public class " + className);
                } else {
                    return "Compilation Error: Java code must contain a 'public class'.";
                }
                
                Path javaFile = tempDirPath.resolve(className + ".java");
                Files.writeString(javaFile, finalCode);

                // Compile the Java code
                String compileOutput = executeProcess(tempDirPath, "javac", javaFile.getFileName().toString());

                if (!compileOutput.isEmpty()) {
                    return "Compilation Error:\n" + compileOutput;
                }
                
                // Run the compiled Java code
                output = executeProcess(tempDirPath, "java", className);
                
            } else if (language.equalsIgnoreCase("cpp")) {
                 // Create C++ file
                Path cppFile = tempDirPath.resolve("main.cpp");
                Files.writeString(cppFile, finalCode);
                
                Path executableFile = tempDirPath.resolve("a.out");

                // Compile C++ code
                String compileOutput = executeProcess(tempDirPath, "g++", "-o", executableFile.getFileName().toString(), cppFile.getFileName().toString());

                if (!compileOutput.isEmpty()) {
                    return "Compilation Error:\n" + compileOutput;
                }
                
                // Run C++ executable
                output = executeProcess(tempDirPath, "./" + executableFile.getFileName().toString());

            } else {
                return "Unsupported language: " + language;
            }

            return output.isEmpty() ? "Execution completed successfully (No output)." : output;

        } catch (Exception e) {
            e.printStackTrace();
            return "Execution Error: " + e.getMessage();
        } finally {
            // Clean up the temporary directory and all its contents
            if (tempDirPath != null) {
                try {
                    Files.walk(tempDirPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (IOException cleanupException) {
                    System.err.println("Failed to clean up temp directory: " + cleanupException.getMessage());
                }
            }
        }
    }

    /**
     * Helper method to execute a process and capture its output, with a timeout.
     */
    private static String executeProcess(Path workingDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Implement a timeout to prevent infinite loops (5 seconds)
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "Execution Timeout (5 seconds exceeded).";
        }
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // If the process failed, return the output (which contains error messages)
        if (process.exitValue() != 0) {
            return output.toString();
        }

        return output.toString();
    }
}
