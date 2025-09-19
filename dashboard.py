import streamlit as st, pandas as pd, joblib

st.title("Predictive Maintenance Dashboard")

model = joblib.load("models/maintenance_model.pkl")
temp = st.slider("Temperature", 50, 150, 90)
vib = st.slider("Vibration", 0, 3, 1)

prediction = model.predict([[temp, vib]])[0]
st.write("⚠ Maintenance Needed!" if prediction==1 else "✅ Machine Healthy")
