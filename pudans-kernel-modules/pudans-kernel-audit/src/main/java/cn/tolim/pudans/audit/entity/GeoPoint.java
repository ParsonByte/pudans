package cn.tolim.pudans.audit.entity;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class GeoPoint implements Serializable {

    @Serial
    private static final long serialVersionUID = -1144169992714000310L;

    private double lat;

    private double lon;

    public GeoPoint() {
    }

    public GeoPoint(double latitude, double longitude) {
        this.lat = latitude;
        this.lon = longitude;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeoPoint geoPoint)) {
            return false;
        }
        return Double.compare(geoPoint.lat, lat) == 0 && Double.compare(geoPoint.lon, lon) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }

    @Override
    public String toString() {
        return "GeoPoint{lat=" + lat + ", lon=" + lon + "}";
    }
}