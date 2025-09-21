import java.io.*;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeRunner {

    public static String runCode(String language, String code) {
        try {
            String output = "";

            if (language.equalsIgnoreCase("python")) {
                // 1. Create a temporary Python file
                File tempFile = File.createTempFile("tempCode", ".py");
                try (FileWriter writer = new FileWriter(tempFile)) {
                    writer.write(code);
                }

                // 2. Determine the correct python command
                String os = System.getProperty("os.name").toLowerCase();
                String pythonCommand;
                if (os.contains("win")) {
                    pythonCommand = "python";
                } else {
                    pythonCommand = "python3";
                }

                // 3. Build and start the process
                ProcessBuilder pb = new ProcessBuilder(pythonCommand, tempFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process process = pb.start();

                // 4. Read the output from the process
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }

                // 5. Wait for the process to finish and clean up
                process.waitFor();
                tempFile.delete();
                output = sb.toString();

            } else if (language.equalsIgnoreCase("java")) {
                // Regex to find and replace the public class name
                Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)", Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(code);
                String className = "TempProgram_" + UUID.randomUUID().toString().replace("-", "");
                String userCode;

                if (matcher.find()) {
                    userCode = matcher.replaceAll("public class " + className);
                } else {
                    return "Compilation Error: No public class found.";
                }

                // Create a temporary Java file with the new class name
                File tempFile = new File(className + ".java");
                userCode = userCode
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t");

                try (FileWriter writer = new FileWriter(tempFile)) {
                    writer.write(userCode);
                }

                // Debugging step: Log the content of the file being compiled
                System.out.println("Code being written to file:");
                System.out.println("---------------------------");
                try (BufferedReader reader = new BufferedReader(new FileReader(tempFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
                System.out.println("---------------------------");

                // Compile the Java code
                ProcessBuilder compilePB = new ProcessBuilder("javac", tempFile.getAbsolutePath());
                compilePB.redirectErrorStream(true);
                Process compileProcess = compilePB.start();

                BufferedReader compileReader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
                StringBuilder compileOutput = new StringBuilder();
                String line;
                while ((line = compileReader.readLine()) != null) {
                    compileOutput.append(line).append("\n");
                }
                compileProcess.waitFor();

                if (compileProcess.exitValue() != 0) {
                    tempFile.delete();
                    new File(className + ".class").delete();
                    return "Compilation Error:\n" + compileOutput.toString();
                }

                // Run the compiled Java code
                ProcessBuilder runPB = new ProcessBuilder("java", "-cp", ".", className);
                runPB.redirectErrorStream(true);
                Process runProcess = runPB.start();

                BufferedReader runReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder runOutput = new StringBuilder();
                while ((line = runReader.readLine()) != null) {
                    runOutput.append(line).append("\n");
                }
                runProcess.waitFor();

                tempFile.delete();
                new File(className + ".class").delete();
                output = runOutput.toString();

            } else {
                return "Unsupported language: " + language;
            }

            return output.isEmpty() ? "No output" : output;

        } catch (Exception e) {
            // Provide a more detailed error message
            return "Execution Error: " + e.getMessage();
        }
    }
}