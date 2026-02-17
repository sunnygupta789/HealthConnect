import React from 'react';
import { StyleSheet, Text, View, TouchableOpacity, Alert } from 'react-native';
import { NativeModules } from 'react-native';

const { WearModule } = NativeModules;

interface ConnectionTestScreenProps {
  onNavigateBack: () => void;
  rawMessage: string;
}

const ConnectionTestScreen: React.FC<ConnectionTestScreenProps> = ({ onNavigateBack, rawMessage }) => {
  const [testResult, setTestResult] = React.useState('');

  const testConnection = async () => {
    try {
      const result = await WearModule.testConnection();
      setTestResult(result);
      Alert.alert("Connection Test", result);
    } catch (error: any) {
      Alert.alert("Error", error.message);
    }
  };

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.backButton} onPress={onNavigateBack}>
        <Text style={styles.backButtonText}>← Back to Vitals</Text>
      </TouchableOpacity>

      <View style={styles.card}>
        <Text style={styles.title}>🔍 Connection Test</Text>
        <Text style={styles.subtitle}>WearOS Communication</Text>
        
        <View style={styles.messageContainer}>
          <Text style={styles.label}>Raw Message from Watch:</Text>
          <Text style={styles.message}>{rawMessage}</Text>
        </View>
        
        <TouchableOpacity style={styles.button} onPress={testConnection}>
          <Text style={styles.buttonText}>Run Connection Test</Text>
        </TouchableOpacity>
        
        {testResult ? (
          <View style={styles.testResult}>
            <Text style={styles.testResultText}>{testResult}</Text>
          </View>
        ) : null}
        
        <Text style={styles.instruction}>
          This page helps verify the connection between your watch and phone.
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
    padding: 20,
  },
  backButton: {
    backgroundColor: '#334155',
    borderRadius: 8,
    padding: 12,
    marginBottom: 20,
    marginTop: 10,
    alignSelf: 'flex-start',
  },
  backButtonText: {
    color: '#F1F5F9',
    fontSize: 14,
    fontWeight: '600',
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 20,
    padding: 30,
    flex: 1,
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
    fontSize: 16,
    color: '#60A5FA',
    fontWeight: '600',
    fontFamily: 'monospace',
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
});

export default ConnectionTestScreen;
