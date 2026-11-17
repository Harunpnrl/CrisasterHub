package com.pnrl.crisasterhub;

import com.pnrl.crisasterhub.dbclasses.Aid_Request;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import javafx.application.Platform;
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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VictimController {

    private static final Logger LOGGER = Logger.getLogger(VictimController.class.getName());
    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "victim-background-task");
        thread.setDaemon(true);
        return thread;
    });

    @FXML private ComboBox<String> categoryCombo;
    @FXML private ToggleGroup urgencyGroup;
    @FXML private ToggleButton urgencyHighBtn;
    @FXML private TextArea descriptionArea;
    @FXML private Button submitRequestBtn;
    @FXML private VBox requestsContainer;
    @FXML private WebView mapWebView;
    @FXML private Label mapStatusLabel;

    private double selectedLatitude = 0.0;
    private double selectedLongitude = 0.0;
    private String selectedCity = "";
    private String selectedDistrict = "";
    private final MapBridge mapBridge = new MapBridge();

    private int currentUserId;
    private volatile boolean locationLookupInProgress;
    private long locationRequestVersion;

    @FXML
    public void initialize() {
        ObservableList<String> categories = FXCollections.observableArrayList(
                "Food", "Water", "Medicine", "Shelter", "Clothing", "Other"
        );
        categoryCombo.setItems(categories);
        submitRequestBtn.setDisable(true);

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

        LOGGER.info("Victim portal initialized successfully.");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            AuthService.clearSession();
            Parent loginRoot = FXMLLoader.load(getClass().getResource("/com/pnrl/crisasterhub/fxml/login.fxml"));
            Scene loginScene = new Scene(loginRoot);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(loginScene);
            stage.setFullScreen(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not return to login screen", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Could not return to the login screen.");
        }
    }

    @FXML
    private void handleSubmitRequest(ActionEvent event) {
        if (currentUserId <= 0) {
            showAlert(Alert.AlertType.ERROR, "Session Error", "Your user session is invalid. Please log in again.");
            return;
        }

        if (locationLookupInProgress) {
            showAlert(Alert.AlertType.INFORMATION, "Location Loading", "Please wait while the selected location is being determined.");
            return;
        }

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

        final int userId = currentUserId;
        final double latitude = selectedLatitude;
        final double longitude = selectedLongitude;
        final String city = selectedCity;
        final String district = selectedDistrict;
        final String category = selectedCategory;
        final String urgency = urgencyLevel;
        final String requestDescription = description.trim();
        submitRequestBtn.setDisable(true);

        CompletableFuture.runAsync(() -> saveRequest(userId, latitude, longitude, city, district,
                        category, urgency, requestDescription), BACKGROUND_EXECUTOR)
                .thenRun(() -> Platform.runLater(() -> {
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
                selectedCity = "";
                selectedDistrict = "";
                mapStatusLabel.setText("CLICK ON MAP TO SET PIN");
                submitRequestBtn.setDisable(true);

                loadRequests();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        submitRequestBtn.setDisable(false);
                        showAlert(Alert.AlertType.ERROR, "Database Error",
                                "Failed to save the request to the database. Please try again.");
                    });
                    return null;
                });
    }

    private void handleCancelRequest(Aid_Request request) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Cancel Request");
        confirmation.setHeaderText("Cancel REQ-" + request.getRequestId() + "?");
        confirmation.setContentText("This action cannot be undone.");

        confirmation.showAndWait().ifPresent(choice -> {
            if (choice != ButtonType.OK) return;

            final int userId = currentUserId;
            CompletableFuture.supplyAsync(() -> deleteRequest(request.getRequestId(), userId), BACKGROUND_EXECUTOR)
                    .thenAccept(deleted -> Platform.runLater(() -> {
                        if (deleted) {
                            showAlert(Alert.AlertType.INFORMATION, "Request Cancelled",
                                    "Your request has been cancelled successfully.");
                            loadRequests();
                        } else {
                            showAlert(Alert.AlertType.WARNING, "Cannot Cancel Request",
                                    "This request may already have an assignment and cannot be deleted.");
                        }
                    }))
                    .exceptionally(error -> {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Database Error",
                                "The request could not be cancelled. Please try again."));
                        return null;
                    });
        });
    }

    private boolean deleteRequest(int requestId, int userId) {
        String findQuery = "SELECT location_id FROM AID_REQUEST " +
                "WHERE request_id = ? AND user_id = ? " +
                "AND LOWER(status) = 'pending' " +
                "AND NOT EXISTS (SELECT 1 FROM ASSIGNMENT WHERE request_id = ?) FOR UPDATE";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int locationId;
                try (PreparedStatement findStatement = conn.prepareStatement(findQuery)) {
                    findStatement.setInt(1, requestId);
                    findStatement.setInt(2, userId);
                    findStatement.setInt(3, requestId);
                    try (ResultSet resultSet = findStatement.executeQuery()) {
                        if (!resultSet.next()) {
                            conn.rollback();
                            return false;
                        }
                        locationId = resultSet.getInt("location_id");
                    }
                }

                try (PreparedStatement deleteRequest = conn.prepareStatement(
                        "DELETE FROM AID_REQUEST WHERE request_id = ? AND user_id = ?")) {
                    deleteRequest.setInt(1, requestId);
                    deleteRequest.setInt(2, userId);
                    if (deleteRequest.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement deleteLocation = conn.prepareStatement(
                        "DELETE FROM LOCATION WHERE location_id = ?")) {
                    deleteLocation.setInt(1, locationId);
                    deleteLocation.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveRequest(int userId, double latitude, double longitude, String city, String district,
                             String category, String urgency, String description) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String locQuery = "INSERT INTO LOCATION (city, district, latitude, longitude) VALUES (?, ?, ?, ?)";
                int locationId;
                try (PreparedStatement locPs = conn.prepareStatement(locQuery, Statement.RETURN_GENERATED_KEYS)) {
                    locPs.setString(1, city);
                    locPs.setString(2, district);
                    locPs.setDouble(3, latitude);
                    locPs.setDouble(4, longitude);
                    locPs.executeUpdate();
                    try (ResultSet rs = locPs.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Failed to get new location ID.");
                        locationId = rs.getInt(1);
                    }
                }

                String reqQuery = "INSERT INTO aid_request " +
                        "(user_id, location_id, category, urgency, description, status, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, NOW())";
                try (PreparedStatement reqPs = conn.prepareStatement(reqQuery)) {
                    reqPs.setInt(1, userId);
                    reqPs.setInt(2, locationId);
                    reqPs.setString(3, category);
                    reqPs.setString(4, urgency);
                    reqPs.setString(5, description);
                    reqPs.setString(6, "PENDING");
                    reqPs.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public class MapBridge {
        public void setLocation(double lat, double lng) {
            LOGGER.fine("Map clicked at: " + lat + ", " + lng);
            final long requestVersion = ++locationRequestVersion;
            locationLookupInProgress = true;
            Platform.runLater(() -> {
                submitRequestBtn.setDisable(true);
                mapStatusLabel.setText("DETERMINING LOCATION...");
            });

            CompletableFuture.supplyAsync(() -> fetchLocationNameFromCoordinates(lat, lng), BACKGROUND_EXECUTOR)
            .thenAccept(locationName -> {

                String[] parts = locationName.split(", ");
                String city = parts[0];
                String district = (parts.length > 1) ? parts[1] : "Unknown";

                Platform.runLater(() -> {
                    if (requestVersion != locationRequestVersion) return;
                    if (mapStatusLabel != null) {
                        String statusText = "Unknown".equalsIgnoreCase(city) && "Unknown".equalsIgnoreCase(district)
                                ? "LOCATION DETAILS UNAVAILABLE"
                                : "SELECTED: " + district.toUpperCase() + ", " + city.toUpperCase();
                        mapStatusLabel.setText(statusText);
                    }

                    selectedLatitude = lat;
                    selectedLongitude = lng;
                    selectedCity = city;
                    selectedDistrict = district;
                    locationLookupInProgress = false;
                    submitRequestBtn.setDisable(false);
                });
            })
            .exceptionally(error -> {
                LOGGER.log(Level.WARNING, "Could not determine selected map location", error);
                Platform.runLater(() -> {
                    if (requestVersion == locationRequestVersion) {
                        locationLookupInProgress = false;
                        mapStatusLabel.setText("LOCATION DETAILS UNAVAILABLE");
                        submitRequestBtn.setDisable(false);
                    }
                });
                return null;
            });
        }
    }

    private String fetchLocationNameFromCoordinates(double lat, double lng) {
        java.net.HttpURLConnection conn = null;
        try {
            String urlString = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + lat + "&longitude=" + lng + "&localityLanguage=tr";

            java.net.URL url = new java.net.URL(urlString);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            conn.connect();

            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream(), "UTF-8")) {
                    while (scanner.hasNextLine()) response.append(scanner.nextLine());
                }

                String json = response.toString();
                String city = extractJsonValue(json, "city");
                if (city.equals("Unknown")) city = extractJsonValue(json, "principalSubdivision");

                String district = extractJsonValue(json, "locality");

                return city + ", " + district;
            } else {
            LOGGER.warning("Reverse-geocoding API returned HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Reverse-geocoding API connection failed", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return "Unknown, Unknown";
    }

    private String extractJsonValue(String json, String key) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not parse reverse-geocoding response", e);
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
        if (currentUserId <= 0) return;

        final int userId = currentUserId;
        CompletableFuture.supplyAsync(() -> fetchRequests(userId))
                .thenAccept(requests -> Platform.runLater(() -> renderRequests(requests)))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Could not load victim requests", error);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Database Error",
                            "Your requests could not be loaded. Please try again."));
                    return null;
                });
    }

    private List<Aid_Request> fetchRequests(int userId) {
        List<Aid_Request> requests = new ArrayList<>();
        String query = "SELECT request_id, user_id, location_id, category, urgency, " +
                "description, status, created_at FROM aid_request " +
                "WHERE user_id = ? AND LOWER(status) IN ('pending', 'in progress') " +
                "ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Aid_Request req = new Aid_Request();
                    req.setRequestId(rs.getInt("request_id"));
                    req.setUserId(rs.getInt("user_id"));
                    req.setLocationId(rs.getInt("location_id"));
                    req.setCategory(rs.getString("category"));
                    req.setUrgency(rs.getString("urgency"));
                    req.setDescription(rs.getString("description"));
                    req.setStatus(rs.getString("status"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    req.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    requests.add(req);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return requests;
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
                    "REQ-" + req.getRequestId() + " • " + safeUpper(req.getCategory())
            );
            idLabel.getStyleClass().add("req-id");

            Label status = new Label(
                    safeUpper(req.getStatus()).replace("_", " ")
            );
            status.getStyleClass().add(getBadgeStyle(req.getStatus()));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox top = new HBox(idLabel, spacer, status);
            top.setSpacing(10);

            Label title = new Label(safeText(req.getUrgency()) + " Priority Request");
            title.getStyleClass().add("req-title");
            title.setStyle("-fx-text-fill: " + getUrgencyColor(req.getUrgency()) + ";");

            Label desc = new Label(safeText(req.getDescription()));
            desc.setWrapText(true);
            desc.getStyleClass().add("req-desc");

            String createdAt = req.getCreatedAt() == null ? "Unknown" : req.getCreatedAt().format(formatter);
            Label footer = new Label("Created at: " + createdAt);
            footer.getStyleClass().add("req-footer");

            Button cancelButton = new Button("Cancel Request");
            cancelButton.getStyleClass().add("cancel-request-btn");
            boolean canCancel = "pending".equals(normalizeStatus(req.getStatus()));
            cancelButton.setDisable(!canCancel);
            if (!canCancel) {
                cancelButton.setTooltip(new Tooltip("Only pending requests can be cancelled."));
            }
            cancelButton.setOnAction(event -> handleCancelRequest(req));

            HBox actions = new HBox(cancelButton);
            actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

            card.getChildren().addAll(top, title, desc, footer, actions);

            requestsContainer.getChildren().add(card);
        }
    }

    private String getBadgeStyle(String status) {
        switch (normalizeStatus(status)) {
            case "pending": return "badge-pending";
            case "in progress":
            case "on the way":
            case "in transit": return "badge-transit";
            case "fulfilled": return "badge-fulfilled";
            default: return "badge-pending";
        }
    }

    private String getCardStyle(String status) {
        switch (normalizeStatus(status)) {
            case "pending": return "req-card-outline";
            case "in progress":
            case "on the way":
            case "in transit": return "req-card-tertiary";
            case "fulfilled": return "req-card-primary";
            default: return "req-card-outline";
        }
    }

    private String getUrgencyColor(String urgency) {
        switch (normalizeStatus(urgency)) {
            case "low": return "#8e909a";
            case "medium": return "#a5c9f9";
            case "high": return "#ffb4ab";
            default: return "#c5c6d0";
        }
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeUpper(String value) {
        return safeText(value).toUpperCase(Locale.ROOT);
    }
}
