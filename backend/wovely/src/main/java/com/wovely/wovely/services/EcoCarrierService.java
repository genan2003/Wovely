package com.wovely.wovely.services;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Random;

@Service
public class EcoCarrierService {

    private static final List<EcoCarrier> ECO_CARRIERS = List.of(
        new EcoCarrier("GreenLogistics", "Bicycle & Electric Van", 0.05),
        new EcoCarrier("EcoParcel", "Carbon-Neutral Fleet", 0.12),
        new EcoCarrier("BioShip", "Bio-fuel Trucks", 0.25),
        new EcoCarrier("EarthFriendly Delivery", "Optimized Route Hybrid", 0.30)
    );

    public static class EcoCarrier {
        private String name;
        private String method;
        private double co2PerKm; // kg

        public EcoCarrier(String name, String method, double co2PerKm) {
            this.name = name;
            this.method = method;
            this.co2PerKm = co2PerKm;
        }

        public String getName() { return name; }
        public String getMethod() { return method; }
        public double getCo2PerKm() { return co2PerKm; }
    }

    /**
     * Automatically selects the best low-CO2 carrier for a route.
     * In a real app, this would use destination distance and carrier availability.
     */
    public EcoCarrier selectBestCarrier(String destination) {
        // Mock selection logic: prioritize lowest CO2 per Km
        // We just return one of the top 2 carriers randomly to simulate variety
        return ECO_CARRIERS.get(new Random().nextInt(2));
    }

    /**
     * Generates a mock prepaid shipping label.
     */
    public String generateLabel(String orderNumber, EcoCarrier carrier, String destination) {
        StringBuilder label = new StringBuilder();
        label.append("--- PREPAID ECO-SHIPPING LABEL ---\n");
        label.append("CARRIER: ").append(carrier.getName()).append("\n");
        label.append("METHOD: ").append(carrier.getMethod()).append("\n");
        label.append("ORDER #: ").append(orderNumber).append("\n");
        label.append("DESTINATION: ").append(destination).append("\n");
        label.append("CO2 RATING: ").append(carrier.getCo2PerKm()).append(" kg/km\n");
        label.append("----------------------------------");
        return label.toString();
    }
}
