import { StyleSheet, Text, View, TouchableOpacity, Alert } from 'react-native'
import React from 'react'
import { NativeEventEmitter, NativeModules } from 'react-native';
import { useEffect, useState } from 'react';
import VitalsScreen from './src/screens/VitalsScreen';
import ConnectionTestScreen from './src/screens/ConnectionTestScreen';

interface VitalsData {
  heartRate: string;
  steps: string;
  distance: string;
  calories: string;
  speed: string;
  gps: string;
}

const App = () => {
  const [rawMessage, setRawMessage] = useState('Waiting for data from watch...');
  const [vitalsData, setVitalsData] = useState<VitalsData>({
    heartRate: '--',
    steps: '0',
    distance: '0.00',
    calories: '0',
    speed: '0.0',
    gps: '⋯',
  });
  const [currentScreen, setCurrentScreen] = useState<'vitals' | 'test'>('vitals');

  const { WearModule } = NativeModules;
  const eventEmitter = WearModule ? new NativeEventEmitter(WearModule) : null;

  useEffect(() => {
    if (!eventEmitter) {
      console.error("WearModule not found! Check your MainApplication.kt registration.");
      return;
    }

    const subscription = eventEmitter.addListener('WearOSMessage', (message: string) => {
      console.log("Received from Watch:", message);
      setRawMessage(message);
      
      // Parse vitals data: Format: HR:78|STEPS:150|DIST:0.11|CAL:6|SPEED:0.5|GPS:✓
      try {
        const parts = message.split('|');
        const newVitals: Partial<VitalsData> = {};
        
        parts.forEach(part => {
          const [key, value] = part.split(':');
          switch(key) {
            case 'HR':
              newVitals.heartRate = value;
              break;
            case 'STEPS':
              newVitals.steps = value;
              break;
            case 'DIST':
              newVitals.distance = value;
              break;
            case 'CAL':
              newVitals.calories = value;
              break;
            case 'SPEED':
              newVitals.speed = value;
              break;
            case 'GPS':
              newVitals.gps = value;
              break;
          }
        });
        
        setVitalsData(prev => ({ ...prev, ...newVitals }));
      } catch (error) {
        console.error("Error parsing vitals data:", error);
      }
    });

    return () => subscription.remove();
  }, []);

  if (currentScreen === 'vitals') {
    return (
      <VitalsScreen
        vitalsData={vitalsData}
        onNavigateToTest={() => setCurrentScreen('test')}
      />
    );
  }

  return (
    <ConnectionTestScreen
      onNavigateBack={() => setCurrentScreen('vitals')}
      rawMessage={rawMessage}
    />
  );
}

export default App