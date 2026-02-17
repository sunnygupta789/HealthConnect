import React from 'react';
import { StyleSheet, Text, View, TouchableOpacity, ScrollView } from 'react-native';

interface VitalsData {
  heartRate: string;
  steps: string;
  distance: string;
  calories: string;
  speed: string;
  gps: string;
}

interface VitalsScreenProps {
  vitalsData: VitalsData;
  onNavigateToTest: () => void;
}

const VitalsScreen: React.FC<VitalsScreenProps> = ({ vitalsData, onNavigateToTest }) => {
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>⌚ Watch Vitals</Text>
        <Text style={styles.subtitle}>Live Health Data</Text>
      </View>

      <ScrollView contentContainerStyle={styles.grid} showsVerticalScrollIndicator={false}>
        <VitalCard
          icon="💓"
          title="Heart Rate"
          value={vitalsData.heartRate}
          unit="BPM"
          color="#EF4444"
        />
        <VitalCard
          icon="👟"
          title="Steps"
          value={vitalsData.steps}
          unit="steps"
          color="#3B82F6"
        />
        <VitalCard
          icon="📏"
          title="Distance"
          value={vitalsData.distance}
          unit="km"
          color="#8B5CF6"
        />
        <VitalCard
          icon="🔥"
          title="Calories"
          value={vitalsData.calories}
          unit="kcal"
          color="#F59E0B"
        />
        <VitalCard
          icon="⚡"
          title="Speed"
          value={vitalsData.speed}
          unit="km/h"
          color="#10B981"
        />
        <VitalCard
          icon="📍"
          title="GPS"
          value={vitalsData.gps}
          unit=""
          color="#06B6D4"
        />
      </ScrollView>

      <TouchableOpacity style={styles.navButton} onPress={onNavigateToTest}>
        <Text style={styles.navButtonText}>🔍 Connection Test</Text>
      </TouchableOpacity>
    </View>
  );
};

interface VitalCardProps {
  icon: string;
  title: string;
  value: string;
  unit: string;
  color: string;
}

const VitalCard: React.FC<VitalCardProps> = ({ icon, title, value, unit, color }) => {
  return (
    <View style={[styles.card, { borderLeftColor: color, borderLeftWidth: 4 }]}>
      <Text style={styles.icon}>{icon}</Text>
      <Text style={styles.cardTitle}>{title}</Text>
      <Text style={styles.value}>{value}</Text>
      {unit ? <Text style={styles.unit}>{unit}</Text> : null}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
    padding: 20,
  },
  header: {
    alignItems: 'center',
    marginBottom: 20,
    marginTop: 10,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#F1F5F9',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 16,
    color: '#94A3B8',
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    paddingBottom: 20,
  },
  card: {
    width: '48%',
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 16,
    marginBottom: 16,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 5,
  },
  icon: {
    fontSize: 32,
    marginBottom: 8,
  },
  cardTitle: {
    fontSize: 12,
    color: '#94A3B8',
    marginBottom: 8,
    textAlign: 'center',
  },
  value: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#F1F5F9',
    marginBottom: 4,
  },
  unit: {
    fontSize: 12,
    color: '#64748B',
  },
  navButton: {
    backgroundColor: '#3B82F6',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginTop: 10,
  },
  navButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default VitalsScreen;
