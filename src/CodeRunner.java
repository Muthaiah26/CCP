import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeRunner {

    public static String runCode(String language, String code) {
        Path tempDirPath = null;
        try {
            // Create a temporary, isolated directory for compilation/execution
            tempDirPath = Files.createTempDirectory("code_exec_" + UUID.randomUUID().toString());
            String output;

            // Clean the code string received from JSON
            String finalCode = cleanJsonString(code);

            // --- Execute based on language ---
            if (language.equalsIgnoreCase("python")) {
                output = executePython(tempDirPath, finalCode);
            } else if (language.equalsIgnoreCase("java")) {
                output = executeJava(tempDirPath, finalCode);
            } else if (language.equalsIgnoreCase("cpp")) {
                output = executeCpp(tempDirPath, finalCode);
            } else {
                return "Unsupported language: " + language;
            }

            return output.isEmpty() ? "Execution successful (No output)." : output;

        } catch (Exception e) {
            e.printStackTrace();
            return "An unexpected error occurred: " + e.getMessage();
        } finally {
            cleanupTempDirectory(tempDirPath);
        }
    }

    /**
     * Clean and unescape JSON string properly.
     * Note: A library like Gson in the server is often better for this.
     */
    private static String cleanJsonString(String code) {
        if (code == null) return "";
        // Basic unescaping for common characters passed in a JSON string
        return code.replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\");
    }

    /**
     * Execute Python code
     */
    private static String executePython(Path tempDirPath, String code) throws IOException, InterruptedException {
        Path tempFile = tempDirPath.resolve("tempCode.py");
        Files.writeString(tempFile, code);

        // --- DOCKER CHANGE ---
        // Inside our Docker container, the command will always be 'python3'.
        // We can remove the check for the operating system.
        String pythonCommand = "python3";
        return executeProcess(tempDirPath, pythonCommand, tempFile.getFileName().toString());
    }

    /**
     * Execute Java code
     */
    private static String executeJava(Path tempDirPath, String code) throws IOException, InterruptedException {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);

        String className;
        if (matcher.find()) {
            className = matcher.group(1);
        } else {
            return "Compilation Error: No 'public class' found in Java code.";
        }

        Path javaFile = tempDirPath.resolve(className + ".java");
        Files.writeString(javaFile, code);

        // Compile the Java code. 'javac' is in the container's PATH.
        String compileOutput = executeProcess(tempDirPath, "javac", javaFile.getFileName().toString());
        if (!compileOutput.isEmpty()) {
            return "Compilation Error:\n" + compileOutput;
        }

        // Run the compiled Java code. 'java' is in the container's PATH.
        return executeProcess(tempDirPath, "java", className);
    }

    /**
     * Execute C++ code
     */
    private static String executeCpp(Path tempDirPath, String code) throws IOException, InterruptedException {
        Path cppFile = tempDirPath.resolve("main.cpp");
        Files.writeString(cppFile, code);

        Path executableFile = tempDirPath.resolve("program");

        // Compile C++ code. 'g++' is in the container's PATH.
        String compileOutput = executeProcess(
            tempDirPath,
            "g++",
            cppFile.getFileName().toString(),
            "-o",
            executableFile.getFileName().toString()
        );

        if (!compileOutput.isEmpty()) {
            return "Compilation Error:\n" + compileOutput;
        }

        // Run C++ executable
        return executeProcess(tempDirPath, "./" + executableFile.getFileName().toString());
    }

    /**
     * Helper method to execute a process and capture its output, with a timeout.
     */
    private static String executeProcess(Path workingDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true); // Combine stdout and stderr
        Process process = pb.start();

        // Timeout to prevent infinite loops (e.g., while(true){})
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "Execution Error: Timeout exceeded (5 seconds).";
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    /**
     * Clean up the temporary directory and all its contents.
     */
    private static void cleanupTempDirectory(Path tempDirPath) {
        if (tempDirPath != null) {
            try {
                // Walk the directory tree and delete files/folders in reverse order
                Files.walk(tempDirPath)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                System.err.println("Failed to clean up temp directory: " + e.getMessage());
            }
        }
    }
}
