import { StyleSheet, Text, View, TouchableOpacity, Alert } from 'react-native'
import React from 'react'
import { NativeEventEmitter, NativeModules } from 'react-native';
import { useEffect, useState } from 'react';



const App = () => {

  const [wearData, setWearData] = useState('Waiting for message from watch...')
  const [testResult, setTestResult] = useState('')


const { WearModule } = NativeModules;
// Add a check to prevent crashes if the module isn't linked yet
const eventEmitter = WearModule ? new NativeEventEmitter(WearModule) : null;

useEffect(() => {
  if (!eventEmitter) {
    console.error("WearModule not found! Check your MainApplication.kt registration.");
    return;
  }

  const subscription = eventEmitter.addListener('WearOSMessage', (message) => {
    console.log("Received from Watch:", message);
    setWearData(message);
  });

  return () => subscription.remove();
}, []);

const testConnection = async () => {
  try {
    const result = await WearModule.testConnection();
    setTestResult(result);
    Alert.alert("Connection Test", result);
  } catch (error) {
    Alert.alert("Error", error.message);
  }
};

  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.title}>📱 Phone App</Text>
        <Text style={styles.subtitle}>WearOS Data Layer Demo</Text>
        
        <View style={styles.messageContainer}>
          <Text style={styles.label}>Message from Watch:</Text>
          <Text style={styles.message}>
            {typeof wearData === 'object' ? JSON.stringify(wearData) : wearData}
          </Text>
        </View>
        
        <TouchableOpacity style={styles.button} onPress={testConnection}>
          <Text style={styles.buttonText}>🔍 Test Connection</Text>
        </TouchableOpacity>
        
        {testResult ? (
          <View style={styles.testResult}>
            <Text style={styles.testResultText}>{testResult}</Text>
          </View>
        ) : null}
        
        <Text style={styles.instruction}>👆 Tap the button on your watch to send a message</Text>
      </View>
    </View>
  )
}

export default App

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 20,
    padding: 30,
    width: '100%',
    maxWidth: 400,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#F1F5F9',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#94A3B8',
    textAlign: 'center',
    marginBottom: 30,
  },
  messageContainer: {
    backgroundColor: '#0F172A',
    borderRadius: 12,
    padding: 20,
    marginBottom: 20,
    borderWidth: 2,
    borderColor: '#3B82F6',
  },
  label: {
    fontSize: 14,
    color: '#94A3B8',
    marginBottom: 8,
    fontWeight: '600',
  },
  message: {
    fontSize: 18,
    color: '#60A5FA',
    fontWeight: '600',
    textAlign: 'center',
  },
  button: {
    backgroundColor: '#3B82F6',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    alignItems: 'center',
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  testResult: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#10B981',
  },
  testResultText: {
    color: '#10B981',
    fontSize: 12,
    fontFamily: 'monospace',
  },
  instruction: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    fontStyle: 'italic',
  },
})