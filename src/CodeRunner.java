import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern
;

public class CodeRunner {

    public static String runCode(String language, String code) {
        Path tempDirPath = null;
        try {
            // Create a temporary, isolated directory for compilation/execution
            tempDirPath = Files.createTempDirectory("code_exec_" + UUID.randomUUID().toString());
            String output;
            
            // --- OPTIMIZED STRING CLEANUP ---
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

            return output.isEmpty() ? "Execution completed successfully (No output)." : output;

        } catch (Exception e) {
            e.printStackTrace();
            return "Execution Error: " + e.getMessage();
        } finally {
            cleanupTempDirectory(tempDirPath);
        }
    }

    /**
     * Clean and unescape JSON string properly
     */
   private static String cleanJsonString(String code) {
    if (code == null || code.isEmpty()) {
        return code;
    }
 
String cleaned = code;
   String prev;
    do {
        prev = cleaned;
        cleaned = cleaned
            .replace("\\\\", "\\")   // \\ -> \
            .replace("\\n", "\n")    // \n -> newline
            .replace("\\r", "\r")    // \r -> carriage return
            .replace("\\t", "\t")    // \t -> tab
            .replace("\\\"", "\"")   // \" -> "
            .replace("\\'", "'");    // \' -> '

             // Remove a backslash that comes immediately before a quote (single or double)
    cleaned = cleaned.replaceAll("\\\\(?=\")", "\"");
    cleaned = cleaned.replaceAll("\\\\(?=')", "'");
    cleaned = cleaned.replaceAll("^\\\\+", "");  // remove leading backslashes
    cleaned = cleaned.replaceAll("\\\\+$", "");  // remove trailing backslashes

    } while (!cleaned.equals(prev));

    // Final pass to remove stray backslashes that precede quotes/parentheses
    cleaned = cleaned.replaceAll("\\\\(?=[\"'()])", "");

    return cleaned;
}


    /**
     * Execute Python code
     */
    private static String executePython(Path tempDirPath, String code) throws IOException, InterruptedException {
        Path tempFile = tempDirPath.resolve("tempCode.py");
        Files.writeString(tempFile, code);

        String pythonCommand = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
        return executeProcess(tempDirPath, pythonCommand, tempFile.getFileName().toString());
    }

    /**
     * Execute Java code
     */
    private static String executeJava(Path tempDirPath, String code) throws IOException, InterruptedException {
        // Find the class name in the code
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);
        
        String className;
        String finalCode = code;
        
        if (matcher.find()) {
            // Use a unique class name to avoid conflicts
            String originalClassName = matcher.group(1);
            className = "TempProgram_" + UUID.randomUUID().toString().replace("-", "");
            
            // Replace the original class name with the new unique one
            finalCode = code.replaceFirst(
                "public\\s+class\\s+" + originalClassName,
                "public class " + className
            );
        } else {
            return "Compilation Error: Java code must contain a 'public class'.";
        }
        
        // Write the code to a file
        Path javaFile = tempDirPath.resolve(className + ".java");
        Files.writeString(javaFile, finalCode);

        // Compile the Java code
        String compileOutput = executeProcess(tempDirPath, "javac", javaFile.getFileName().toString());

        if (!compileOutput.isEmpty()) {
            return "Compilation Error:\n" + compileOutput;
        }
        
        // Run the compiled Java code
        return executeProcess(tempDirPath, "java", className);
    }

    /**
     * Execute C++ code
     */
    private static String executeCpp(Path tempDirPath, String code) throws IOException, InterruptedException {
        // Create C++ file
        Path cppFile = tempDirPath.resolve("main.cpp");
        Files.writeString(cppFile, code);
        
        Path executableFile = tempDirPath.resolve("a.out");

        // Compile C++ code
        String compileOutput = executeProcess(
            tempDirPath, 
            "g++", 
            "-o", 
            executableFile.getFileName().toString(), 
            cppFile.getFileName().toString()
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
        
        // Clean the output to remove backslashes and escape sequences
        String result = output.toString();
        result = cleanOutput(result);
        
        // If the process failed, return the cleaned output (which contains error messages)
        if (process.exitValue() != 0) {
            return result;
        }

        return result;
    }
    
    /**
     * Clean output by removing unwanted backslashes and escape sequences
     */
    private static String cleanOutput(String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }
        
        // Remove escaped backslashes that shouldn't be in the output
        String cleaned = output;
        
        // Remove \\n, \\t, \\r patterns (double backslash with character)
        cleaned = cleaned.replace("\\\\n", "");
        cleaned = cleaned.replace("\\\\t", "");
        cleaned = cleaned.replace("\\\\r", "");
        
        // Remove single backslash followed by space or at end of line
        cleaned = cleaned.replaceAll("\\\\\\s+", " ");
        cleaned = cleaned.replaceAll("\\\\$", "");
        cleaned = cleaned.replaceAll("\\\\(?=\\n)", "");
        
        // Remove any remaining standalone backslashes that are not part of valid escape sequences
        // Keep backslashes only if they are part of valid paths (Windows paths)
        // Remove backslashes that appear before quotes, parentheses, or other special chars
        cleaned = cleaned.replaceAll("\\\\(?=[\"'()])", "");
        
        return cleaned;
    }

    /**
     * Clean up the temporary directory and all its contents
     */
    private static void cleanupTempDirectory(Path tempDirPath) {
        if (tempDirPath != null) {
            try {
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