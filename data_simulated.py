import random, csv, time

with open("data/sensor_data.csv", "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["timestamp", "temperature", "vibration", "label"])
    for i in range(1000):
        temp = random.uniform(60, 120)  
        vib = random.uniform(0.1, 2.0)  
        label = 1 if temp > 100 or vib > 1.5 else 0  
        writer.writerow([i, temp, vib, label])
