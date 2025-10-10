# Use a standard Ubuntu image as our base
FROM ubuntu:22.04

# Set the author label (good practice)
LABEL maintainer="your-name"

# Set the working directory inside the container
WORKDIR /app

# Update package lists and install all compilers and runtimes.
RUN apt-get update && \
    apt-get install -y \
    openjdk-17-jdk \
    python3 \
    build-essential && \
    rm -rf /var/lib/apt/lists/*

# Copy the dependency library (gson.jar)
COPY lib ./lib
COPY build ./build



# Expose the port your Java server listens on (e.g., 8080)
EXPOSE 8080

# Command to run your Java server when the container starts
CMD ["java", "-cp", "build:lib/gson-2.13.2.jar", "CodeExecutorServer"]

