import pandas as pd, joblib

model = joblib.load("models/maintenance_model.pkl")
new_data = pd.DataFrame([[110, 1.6]], columns=["temperature", "vibration"])
prediction = model.predict(new_data)[0]

print("⚠ Maintenance Required!" if prediction == 1 else "✅ Machine Healthy")
