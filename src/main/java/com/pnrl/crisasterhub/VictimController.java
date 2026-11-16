package com.pnrl.crisasterhub;

import com.pnrl.crisasterhub.dbclasses.Aid_Request;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class VictimController {

    @FXML private ComboBox<String> categoryCombo;
    @FXML private ToggleGroup urgencyGroup;
    @FXML private ToggleButton urgencyHighBtn;
    @FXML private TextArea descriptionArea;
    @FXML private VBox requestsContainer;
    @FXML private WebView mapWebView;
    @FXML private Label mapStatusLabel;

    private double selectedLatitude = 0.0;
    private double selectedLongitude = 0.0;
    private String selectedCity = "";
    private String selectedDistrict = "";
    private final MapBridge mapBridge = new MapBridge();

    private int currentUserId = 1;

    @FXML
    public void initialize() {
        ObservableList<String> categories = FXCollections.observableArrayList(
                "Food", "Water", "Medicine", "Shelter", "Clothing", "Other"
        );
        categoryCombo.setItems(categories);

        WebEngine webEngine = mapWebView.getEngine();

        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                netscape.javascript.JSObject window = (netscape.javascript.JSObject) webEngine.executeScript("window");
                window.setMember("javaConnector", mapBridge);
            }
        });

        String mapHtml = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n" +
                "    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n" +
                "    <style>\n" +
                "        body { margin: 0; padding: 0; background: #081328; }\n" +
                "        #map { width: 100%; height: 100vh; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"map\"></div>\n" +
                "    <script>\n" +
                "        var map = L.map('map').setView([39.7767, 30.5206], 13);\n" +
                "        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {\n" +
                "            attribution: '&copy; OpenStreetMap'\n" +
                "        }).addTo(map);\n" +
                "        var marker;\n" +
                "        map.on('click', function(e) {\n" +
                "            var lat = e.latlng.lat;\n" +
                "            var lng = e.latlng.lng;\n" +
                "            if (marker) { map.removeLayer(marker); }\n" +
                "            marker = L.marker([lat, lng]).addTo(map);\n" +
                "            if (typeof javaConnector !== 'undefined') {\n" +
                "                javaConnector.setLocation(lat, lng);\n" +
                "            }\n" +
                "        });\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";

        webEngine.loadContent(mapHtml);

        System.out.println("Victim Portal initialized successfully.");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            Parent loginRoot = FXMLLoader.load(getClass().getResource("/com/pnrl/crisasterhub/fxml/login.fxml"));
            Scene loginScene = new Scene(loginRoot);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(loginScene);
            stage.setFullScreen(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not return to the login screen.");
        }
    }

    @FXML
    private void handleSubmitRequest(ActionEvent event) {
        String selectedCategory = categoryCombo.getValue();
        String description = descriptionArea.getText();

        ToggleButton selectedUrgencyBtn = (ToggleButton) urgencyGroup.getSelectedToggle();
        String urgencyLevel = null;
        if (selectedUrgencyBtn != null) {
            urgencyLevel = selectedUrgencyBtn.getText().toUpperCase();
        }

        if (selectedCategory == null || description == null || description.trim().isEmpty() || urgencyLevel == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Information", "Please select a category, urgency, and describe your need.");
            return;
        }

        if (selectedLatitude == 0.0 && selectedLongitude == 0.0) {
            showAlert(Alert.AlertType.WARNING, "Missing Location", "Please click on the map to set your exact location.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String locQuery = "INSERT INTO LOCATION (city, district, latitude, longitude) VALUES (?, ?, ?, ?)";

                int newLocationId = -1;
                try (PreparedStatement locPs = conn.prepareStatement(locQuery, Statement.RETURN_GENERATED_KEYS)) {
                    locPs.setString(1, selectedCity);
                    locPs.setString(2, selectedDistrict);
                    locPs.setDouble(3, selectedLatitude);
                    locPs.setDouble(4, selectedLongitude);
                    locPs.executeUpdate();

                    try (ResultSet rs = locPs.getGeneratedKeys()) {
                        if (rs.next()) {
                            newLocationId = rs.getInt(1);
                        } else {
                            throw new SQLException("Failed to get new Location ID.");
                        }
                    }
                }

                String reqQuery = "INSERT INTO aid_request (user_id, location_id, category, urgency, description, status, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, NOW())";

                try (PreparedStatement reqPs = conn.prepareStatement(reqQuery)) {
                    reqPs.setInt(1, currentUserId);
                    reqPs.setInt(2, newLocationId);
                    reqPs.setString(3, selectedCategory);
                    reqPs.setString(4, urgencyLevel);
                    reqPs.setString(5, description.trim());
                    reqPs.setString(6, "PENDING");
                    reqPs.executeUpdate();
                }

                conn.commit();

                showAlert(Alert.AlertType.INFORMATION, "Request Sent", "Your emergency request has been successfully submitted.");

                categoryCombo.getSelectionModel().clearSelection();
                if (urgencyHighBtn != null) {
                    urgencyHighBtn.setSelected(true);
                } else {
                    urgencyGroup.selectToggle(null);
                }
                descriptionArea.clear();

                selectedLatitude = 0.0;
                selectedLongitude = 0.0;
                mapStatusLabel.setText("CLICK ON MAP TO SET PIN");

                loadRequests();

            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save the request to the database.");
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Connection Error", "Could not connect to the database. Please try again.");
        }
    }

    public class MapBridge {
        public void setLocation(double lat, double lng) {
            System.out.println("Map clicked at: " + lat + ", " + lng);

            new Thread(() -> {
                String locationName = fetchLocationNameFromCoordinates(lat, lng);

                String[] parts = locationName.split(", ");
                String city = parts[0];
                String district = (parts.length > 1) ? parts[1] : "Unknown";

                javafx.application.Platform.runLater(() -> {
                    if (mapStatusLabel != null) {
                        mapStatusLabel.setText("SELECTED: " + district.toUpperCase() + ", " + city.toUpperCase());
                    }

                    selectedLatitude = lat;
                    selectedLongitude = lng;
                    selectedCity = city;
                    selectedDistrict = district;
                });
            }).start();
        }
    }

    private String fetchLocationNameFromCoordinates(double lat, double lng) {
        try {
            String urlString = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + lat + "&longitude=" + lng + "&localityLanguage=tr";

            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            conn.connect();

            if (conn.getResponseCode() == 200) {
                java.util.Scanner scanner = new java.util.Scanner(url.openStream(), "UTF-8");
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) response.append(scanner.nextLine());
                scanner.close();

                String json = response.toString();
                System.out.println("BigDataCloud JSON: " + json);

                String city = extractJsonValue(json, "city");
                if (city.equals("Unknown")) city = extractJsonValue(json, "principalSubdivision");

                String district = extractJsonValue(json, "locality");

                return city + ", " + district;
            } else {
                System.out.println("API Error: HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            System.out.println("API Connection failed: " + e.getMessage());
        }
        return "Unknown, Unknown";
    }

    private String extractJsonValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Unknown";
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        loadRequests();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void loadRequests() {
        List<Aid_Request> requests = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = "SELECT * FROM aid_request WHERE user_id = ? ORDER BY created_at DESC";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, currentUserId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Aid_Request req = new Aid_Request();

                req.setRequestId(rs.getInt("request_id"));
                req.setUserId(rs.getInt("user_id"));
                req.setLocationId(rs.getInt("location_id"));
                req.setCategory(rs.getString("category"));
                req.setUrgency(rs.getString("urgency"));
                req.setDescription(rs.getString("description"));
                req.setStatus(rs.getString("status"));
                req.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

                requests.add(req);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        renderRequests(requests);
    }

    private void renderRequests(List<Aid_Request> requests) {
        requestsContainer.getChildren().clear();

        if (requests.isEmpty()) {
            Label emptyLabel = new Label("You have no active emergency requests yet.");
            emptyLabel.getStyleClass().add("req-desc");
            requestsContainer.getChildren().add(emptyLabel);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm");

        for (Aid_Request req : requests) {
            VBox card = new VBox();
            card.getStyleClass().addAll("req-card", getCardStyle(req.getStatus()));
            card.setSpacing(10);

            Label idLabel = new Label(
                    "REQ-" + req.getRequestId() + " • " + req.getCategory().toUpperCase()
            );
            idLabel.getStyleClass().add("req-id");

            Label status = new Label(
                    req.getStatus().replace("_", " ").toUpperCase()
            );
            status.getStyleClass().add(getBadgeStyle(req.getStatus()));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox top = new HBox(idLabel, spacer, status);
            top.setSpacing(10);

            Label title = new Label(req.getUrgency() + " Priority Request");
            title.getStyleClass().add("req-title");
            title.setStyle("-fx-text-fill: " + getUrgencyColor(req.getUrgency()) + ";");

            Label desc = new Label(req.getDescription());
            desc.setWrapText(true);
            desc.getStyleClass().add("req-desc");

            Label footer = new Label(
                    "Created at: " + req.getCreatedAt().format(formatter)
            );
            footer.getStyleClass().add("req-footer");

            card.getChildren().addAll(top, title, desc, footer);

            requestsContainer.getChildren().add(card);
        }
    }

    private String getBadgeStyle(String status) {
        switch (status.toLowerCase()) {
            case "pending": return "badge-pending";
            case "in_transit": return "badge-transit";
            case "fulfilled": return "badge-fulfilled";
            default: return "badge-pending";
        }
    }

    private String getCardStyle(String status) {
        switch (status.toLowerCase()) {
            case "pending": return "req-card-outline";
            case "in_transit": return "req-card-tertiary";
            case "fulfilled": return "req-card-primary";
            default: return "req-card-outline";
        }
    }

    private String getUrgencyColor(String urgency) {
        switch (urgency.toLowerCase()) {
            case "low": return "#8e909a";
            case "medium": return "#a5c9f9";
            case "high": return "#ffb4ab";
            default: return "#c5c6d0";
        }
    }
}
