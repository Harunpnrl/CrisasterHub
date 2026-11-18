package com.pnrl.crisasterhub;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pnrl.crisasterhub.dbclasses.Aid_Request;
import com.pnrl.crisasterhub.dbclasses.Resource;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VolunteerController {

    private static final Logger LOGGER = Logger.getLogger(VolunteerController.class.getName());
    private static final double DEFAULT_VOLUNTEER_RADIUS_KM = 50.0;
    private static final double VOLUNTEER_RADIUS_KM = configuredRadiusKm();
    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "volunteer-background-task");
        thread.setDaemon(true);
        return thread;
    });

    // Sol Form Bileşenleri
    @FXML private ComboBox<String> resourceCategoryCombo;
    @FXML private TextField resourceQuantityField;
    @FXML private TextArea resourceDescriptionArea;
    @FXML private WebView volunteerMapWebView;
    @FXML private Label volunteerMapStatusLabel;

    // My Active Tasks
    @FXML private Label activeTaskCountBadge;
    @FXML private FlowPane activeTasksContainer;

    // Available Tasks
    @FXML private TextField taskSearchField;
    @FXML private VBox availableTasksContainer;

    // My Resources Table
    @FXML private Label resourceCountBadge;
    @FXML private TableView<ResourceItem> resourcesTable;
    @FXML private TableColumn<ResourceItem, String> colResCategory;
    @FXML private TableColumn<ResourceItem, Integer> colResAmount;
    @FXML private TableColumn<ResourceItem, String> colResDescription;
    @FXML private TableColumn<ResourceItem, Void> colResActions;
    @FXML private VBox emptyResourcesState;

    private int currentUserId;
    private double selectedLatitude;
    private double selectedLongitude;
    private String selectedCity = "";
    private String selectedDistrict = "";
    private volatile boolean locationLookupInProgress;
    private long locationRequestVersion;
    private final MapBridge mapBridge = new MapBridge();
    private final ObservableList<Aid_Request> allAvailableTasks = FXCollections.observableArrayList();
    private FilteredList<Aid_Request> filteredAvailableTasks;

    // Veritabanı resource_id bilgisini tutan iç model
    public static class ResourceItem extends Resource {
        private final int resourceId;

        public ResourceItem(int resourceId, String category, int quantity, String description) {
            super(category, quantity, description);
            this.resourceId = resourceId;
        }

        public int getResourceId() {
            return resourceId;
        }
    }

    private record ActiveTask(int assignmentId, int requestId, String category, String description,
                              String status, int quantity, String city, String district) {}

    private record ResourceLocation(String category, int quantity, int reservedQuantity,
                                    Double latitude, Double longitude, String city, String district) {}

    @FXML
    public void initialize() {
        ObservableList<String> categories = FXCollections.observableArrayList(
                "Food", "Water", "Medicine", "Shelter", "Clothing", "Other"
        );
        resourceCategoryCombo.setItems(categories);

        WebEngine webEngine = volunteerMapWebView.getEngine();
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                javafx.scene.web.WebEngine engine = volunteerMapWebView.getEngine();
                netscape.javascript.JSObject window =
                        (netscape.javascript.JSObject) engine.executeScript("window");
                window.setMember("javaConnector", mapBridge);
            }
        });
        webEngine.loadContent(createMapHtml());

        // Sadece rakam girişine izin ver
        resourceQuantityField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                resourceQuantityField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        // My Resources Tablo Sütunları
        colResCategory.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCategory()));
        colResAmount.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getQuantity()));
        colResDescription.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescription()));

        setupResourceActionsColumn();

        // Available Tasks Arama Filtresi (Açıklama bazlı)
        filteredAvailableTasks = new FilteredList<>(allAvailableTasks, p -> true);
        if (taskSearchField != null) {
            taskSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
                String filter = (newVal == null) ? "" : newVal.trim().toLowerCase();
                filteredAvailableTasks.setPredicate(task -> {
                    if (filter.isEmpty()) return true;
                    return String.valueOf(task.getRequestId()).contains(filter)
                            || safeText(task.getCategory()).toLowerCase(Locale.ROOT).contains(filter)
                            || safeText(task.getUrgency()).toLowerCase(Locale.ROOT).contains(filter)
                            || safeText(task.getDescription()).toLowerCase(Locale.ROOT).contains(filter);
                });
                renderAvailableTaskCards();
            });
        }

        LOGGER.info("Volunteer portal initialized.");
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        loadMyResources();
        loadAvailableTasks();
        loadActiveTasks();
    }

    // =========================================================================
    // 1. SUBMIT & MANAGE RESOURCES
    // =========================================================================

    @FXML
    private void handleRegisterResource(ActionEvent event) {
        if (currentUserId <= 0) {
            showAlert(Alert.AlertType.ERROR, "Session Error", "Your user session is invalid. Please log in again.");
            return;
        }
        String categoryUi = resourceCategoryCombo.getValue();
        String quantityStr = resourceQuantityField.getText();
        String description = resourceDescriptionArea.getText();

        if (categoryUi == null || quantityStr == null || quantityStr.isEmpty() || description == null || description.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Incomplete Form", "Please fill out all fields before registering a resource.");
            return;
        }

        if (locationLookupInProgress) {
            showAlert(Alert.AlertType.INFORMATION, "Location Loading", "Please wait while your location is being determined.");
            return;
        }
        if (selectedLatitude == 0.0 && selectedLongitude == 0.0) {
            showAlert(Alert.AlertType.WARNING, "Missing Location", "Please click your current location on the map.");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid Quantity", "Please enter a valid quantity.");
            return;
        }
        if (quantity <= 0) {
            showAlert(Alert.AlertType.WARNING, "Invalid Quantity", "Quantity must be greater than 0.");
            return;
        }

        String dbCategory = categoryUi.toLowerCase(Locale.ROOT);
        final int userId = currentUserId;
        final double latitude = selectedLatitude;
        final double longitude = selectedLongitude;
        final String city = selectedCity;
        final String district = selectedDistrict;
        final String resourceDescription = description.trim();
        CompletableFuture.runAsync(() -> registerResource(userId, latitude, longitude, city, district,
                        dbCategory, quantity, resourceDescription), BACKGROUND_EXECUTOR)
                .thenRun(() -> Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Resource successfully registered into the network.");
                    resourceCategoryCombo.setValue(null);
                    resourceQuantityField.clear();
                    resourceDescriptionArea.clear();
                    selectedLatitude = 0.0;
                    selectedLongitude = 0.0;
                    selectedCity = "";
                    selectedDistrict = "";
                    volunteerMapStatusLabel.setText("CLICK MAP TO SET LOCATION");
                    loadMyResources();
                    loadAvailableTasks();
                }))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Could not register resource", error);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Database Error",
                            "Failed to save the resource. Please try again."));
                    return null;
                });
    }

    private void registerResource(int userId, double latitude, double longitude, String city,
                                  String district, String category, int quantity, String description) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int locationId;
                try (PreparedStatement location = conn.prepareStatement(
                        "INSERT INTO LOCATION (city, district, latitude, longitude) VALUES (?, ?, ?, ?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    location.setString(1, city);
                    location.setString(2, district);
                    location.setDouble(3, latitude);
                    location.setDouble(4, longitude);
                    location.executeUpdate();
                    try (ResultSet keys = location.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Location ID was not generated");
                        locationId = keys.getInt(1);
                    }
                }

                try (PreparedStatement resource = conn.prepareStatement(
                        "INSERT INTO RESOURCE (user_id, location_id, category, quantity, description) VALUES (?, ?, ?, ?, ?)")) {
                    resource.setInt(1, userId);
                    resource.setInt(2, locationId);
                    resource.setString(3, category);
                    resource.setInt(4, quantity);
                    resource.setString(5, description);
                    resource.executeUpdate();
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

    private void loadMyResources() {
        if (currentUserId <= 0) return;
        final int userId = currentUserId;
        CompletableFuture.supplyAsync(() -> fetchResources(userId), BACKGROUND_EXECUTOR)
                .thenAccept(resources -> Platform.runLater(() -> {
                    resourcesTable.setItems(FXCollections.observableArrayList(resources));
                    int count = resources.size();
                    resourceCountBadge.setText(count + " LISTED");
                    boolean empty = count == 0;
                    emptyResourcesState.setVisible(empty);
                    emptyResourcesState.setManaged(empty);
                }))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Failed to load resources for user " + userId, error);
                    return null;
                });
    }

    private java.util.List<ResourceItem> fetchResources(int userId) {
        java.util.List<ResourceItem> resources = new java.util.ArrayList<>();
        String query = "SELECT resource_id, category, quantity, description FROM RESOURCE " +
                "WHERE user_id = ? AND quantity > 0 ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rawCategory = rs.getString("category");
                    String displayCategory = rawCategory == null || rawCategory.isBlank()
                            ? "Unknown"
                            : rawCategory.substring(0, 1).toUpperCase(Locale.ROOT) + rawCategory.substring(1);
                    resources.add(new ResourceItem(rs.getInt("resource_id"), displayCategory,
                            rs.getInt("quantity"), rs.getString("description")));
                }
            }
            return resources;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupResourceActionsColumn() {
        colResActions.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(8, editBtn, deleteBtn);

            {
                box.setAlignment(Pos.CENTER_RIGHT);
                editBtn.getStyleClass().add("secondary-action-btn");
                deleteBtn.getStyleClass().add("delete-action-btn");

                editBtn.setOnAction(e -> {
                    ResourceItem item = getTableView().getItems().get(getIndex());
                    handleEditResource(item);
                });

                deleteBtn.setOnAction(e -> {
                    ResourceItem item = getTableView().getItems().get(getIndex());
                    handleDeleteResource(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void handleEditResource(ResourceItem item) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Resource Quantity");
        dialog.setHeaderText("Update " + item.getCategory() + " resource");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField quantityField = new TextField(String.valueOf(item.getQuantity()));
        TextArea descriptionField = new TextArea(safeText(item.getDescription()));
        descriptionField.setWrapText(true);
        descriptionField.setPrefRowCount(3);
        VBox content = new VBox(10,
                new Label("Quantity:"), quantityField,
                new Label("Description:"), descriptionField);
        content.setPrefWidth(360);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            int newQty;
            try {
                newQty = Integer.parseInt(quantityField.getText().trim());
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.WARNING, "Invalid Input", "Please enter a valid quantity.");
                return;
            }
            String newDescription = descriptionField.getText() == null ? "" : descriptionField.getText().trim();
            if (newQty < 0 || newDescription.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Input", "Quantity and description are required.");
                return;
            }
            CompletableFuture.runAsync(() -> updateResource(item.getResourceId(), newQty, newDescription), BACKGROUND_EXECUTOR)
                    .thenRun(() -> Platform.runLater(() -> {
                        loadMyResources();
                        loadAvailableTasks();
                    }))
                    .exceptionally(error -> {
                        LOGGER.log(Level.WARNING, "Could not update resource quantity", error);
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Database Error",
                                "Could not update resource."));
                        return null;
                    });
        });
    }

    private void handleDeleteResource(ResourceItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Resource");
        confirm.setHeaderText("Remove " + item.getCategory() + "?");
        confirm.setContentText("Are you sure you want to delete this resource from your inventory?");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.OK) {
            CompletableFuture.runAsync(() -> deleteResource(item.getResourceId()), BACKGROUND_EXECUTOR)
                    .thenRun(() -> Platform.runLater(() -> {
                        loadMyResources();
                        loadAvailableTasks();
                    }))
                    .exceptionally(error -> {
                        LOGGER.log(Level.WARNING, "Could not delete resource", error);
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Database Error",
                                "Could not delete resource because it is linked to an assignment."));
                        return null;
                    });
        }
    }

    private void updateResource(int resourceId, int newQuantity, String description) {
        String reservedQuery = "SELECT COALESCE(SUM(assigned_quantity), 0) FROM ASSIGNMENT " +
                "WHERE resource_id = ? AND LOWER(status) IN ('assigned', 'on the way')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement reserved = conn.prepareStatement(reservedQuery)) {
            reserved.setInt(1, resourceId);
            try (ResultSet rs = reserved.executeQuery()) {
                if (rs.next() && newQuantity < rs.getInt(1)) {
                    throw new SQLException("Quantity cannot be lower than reserved assignment quantity");
                }
            }
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE RESOURCE SET quantity = ?, description = ? WHERE resource_id = ? AND user_id = ?")) {
                update.setInt(1, newQuantity);
                update.setString(2, description);
                update.setInt(3, resourceId);
                update.setInt(4, currentUserId);
                if (update.executeUpdate() != 1) throw new SQLException("Resource not found");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteResource(int resourceId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement check = conn.prepareStatement(
                     "SELECT COUNT(*) FROM ASSIGNMENT WHERE resource_id = ?")) {
            check.setInt(1, resourceId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) throw new SQLException("Resource has active assignment");
            }
            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM RESOURCE WHERE resource_id = ? AND user_id = ?")) {
                delete.setInt(1, resourceId);
                delete.setInt(2, currentUserId);
                if (delete.executeUpdate() != 1) throw new SQLException("Resource not found");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 2. AVAILABLE TASKS (KART LİSTESİ + KATEGORİ FİLTRESİ + ARAMA)
    // =========================================================================

    private void loadAvailableTasks() {
        if (currentUserId <= 0) return;
        final int userId = currentUserId;
        CompletableFuture.supplyAsync(() -> fetchAvailableTasks(userId), BACKGROUND_EXECUTOR)
                .thenAccept(tasks -> Platform.runLater(() -> {
                    allAvailableTasks.setAll(tasks);
                    renderAvailableTaskCards();
                }))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Failed to load available tasks", error);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Task Load Error",
                            "Available tasks could not be loaded. Please check the database connection and location data."));
                    return null;
                });
    }

    private java.util.List<Aid_Request> fetchAvailableTasks(int userId) {
        java.util.List<Aid_Request> tasks = new java.util.ArrayList<>();
        String requestQuery = "SELECT req.request_id, req.user_id, req.location_id, req.category, " +
                "req.urgency, req.description, req.status, req.created_at, " +
                "reqLoc.city, reqLoc.district, reqLoc.latitude, reqLoc.longitude " +
                "FROM AID_REQUEST req JOIN LOCATION reqLoc ON req.location_id=reqLoc.location_id " +
                "WHERE LOWER(req.status)='pending' AND req.user_id<>?";
        String resourceQuery = "SELECT resource.category, resource.quantity, " +
                "COALESCE((SELECT SUM(reserved.assigned_quantity) FROM ASSIGNMENT reserved " +
                "WHERE reserved.resource_id=resource.resource_id " +
                "AND LOWER(reserved.status) IN ('assigned','on the way')),0) reserved_quantity, " +
                "loc.latitude, loc.longitude, loc.city, loc.district " +
                "FROM RESOURCE resource JOIN LOCATION loc ON resource.location_id=loc.location_id " +
                "WHERE resource.user_id=? AND resource.quantity>0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement requests = conn.prepareStatement(requestQuery);
             PreparedStatement resources = conn.prepareStatement(resourceQuery)) {
            requests.setInt(1, userId);
            java.util.List<ResourceLocation> availableResources = new java.util.ArrayList<>();
            resources.setInt(1, userId);
            try (ResultSet rs = resources.executeQuery()) {
                while (rs.next()) {
                    availableResources.add(new ResourceLocation(rs.getString("category"),
                            rs.getInt("quantity"), rs.getInt("reserved_quantity"),
                            nullableDouble(rs, "latitude"), nullableDouble(rs, "longitude"),
                            rs.getString("city"), rs.getString("district")));
                }
            }
            try (ResultSet rs = requests.executeQuery()) {
                while (rs.next()) {
                    Double requestLatitude = nullableDouble(rs, "latitude");
                    Double requestLongitude = nullableDouble(rs, "longitude");
                    double nearestDistance = Double.MAX_VALUE;
                    for (ResourceLocation resource : availableResources) {
                        if (!safeText(resource.category()).equalsIgnoreCase(safeText(rs.getString("category")))
                                || resource.quantity() - resource.reservedQuantity() < 1) continue;
                        double distance = distanceKm(requestLatitude, requestLongitude,
                                resource.latitude(), resource.longitude());
                        boolean sameRegion = sameRegion(rs.getString("city"), rs.getString("district"),
                                resource.city(), resource.district());
                        if (distance <= VOLUNTEER_RADIUS_KM || sameRegion) {
                            double effectiveDistance = distance == Double.MAX_VALUE ? 0 : distance;
                            nearestDistance = Math.min(nearestDistance, effectiveDistance);
                        }
                    }
                    if (nearestDistance == Double.MAX_VALUE) continue;
                    Aid_Request task = new Aid_Request(rs.getInt("request_id"), rs.getInt("user_id"),
                            rs.getInt("location_id"), rs.getString("city") + ", " + rs.getString("district"),
                            rs.getString("category"), rs.getString("urgency"), rs.getString("description"),
                            rs.getString("status"), rs.getTimestamp("created_at") == null ? null :
                            rs.getTimestamp("created_at").toLocalDateTime());
                    task.setDistanceKm(nearestDistance == Double.MAX_VALUE ? 0 : nearestDistance);
                    tasks.add(task);
                }
            }
            tasks.sort(java.util.Comparator.comparingInt((Aid_Request task) -> urgencyRank(task.getUrgency()))
                    .thenComparingDouble((Aid_Request task) -> task.getDistanceKm())
                    .thenComparingInt((Aid_Request task) -> task.getRequestId()));
            return tasks;
        } catch (SQLException e) {
            throw new RuntimeException("Could not load nearby tasks", e);
        }
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static double distanceKm(Double firstLatitude, Double firstLongitude,
                                     Double secondLatitude, Double secondLongitude) {
        if (firstLatitude == null || firstLongitude == null || secondLatitude == null || secondLongitude == null) {
            return Double.MAX_VALUE;
        }
        double latDistance = Math.toRadians(secondLatitude - firstLatitude);
        double lonDistance = Math.toRadians(secondLongitude - firstLongitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static boolean sameRegion(String requestCity, String requestDistrict,
                                      String resourceCity, String resourceDistrict) {
        return equalsRegion(requestCity, resourceCity) || equalsRegion(requestDistrict, resourceDistrict);
    }

    private static boolean equalsRegion(String first, String second) {
        return first != null && second != null && !first.isBlank() && !second.isBlank()
                && first.trim().equalsIgnoreCase(second.trim());
    }

    private static int urgencyRank(String urgency) {
        if ("high".equalsIgnoreCase(urgency)) return 0;
        if ("medium".equalsIgnoreCase(urgency)) return 1;
        return 2;
    }

    private void renderAvailableTaskCards() {
        availableTasksContainer.getChildren().clear();

        if (filteredAvailableTasks == null || filteredAvailableTasks.isEmpty()) {
            Label emptyLbl = new Label("No matching available tasks for your registered resource categories.");
            emptyLbl.getStyleClass().add("task-meta");
            availableTasksContainer.getChildren().add(emptyLbl);
            return;
        }

        for (Aid_Request req : filteredAvailableTasks) {
            HBox row = new HBox(16);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("available-task-row");

            VBox infoBox = new VBox(4);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            Label titleLbl = new Label("REQ-" + req.getRequestId() + " • " +
                    safeUpper(req.getCategory()) + " Request");
            titleLbl.getStyleClass().add("task-title");

            Label descLbl = new Label(safeText(req.getDescription()));
            descLbl.setWrapText(true);
            descLbl.getStyleClass().add("task-meta");

            infoBox.getChildren().addAll(titleLbl, descLbl);
            Label distanceLbl = new Label(String.format(Locale.ROOT, "%.1f km away", req.getDistanceKm()));
            distanceLbl.getStyleClass().add("task-meta");
            infoBox.getChildren().add(distanceLbl);

            Label urgencyBadge = new Label(safeUpper(req.getUrgency()));
            urgencyBadge.getStyleClass().add(
                    "HIGH".equalsIgnoreCase(req.getUrgency()) ? "urgency-badge-high" : "urgency-badge-medium"
            );

            Button claimBtn = new Button("Claim");
            claimBtn.getStyleClass().add("claim-btn");
            claimBtn.setOnAction(e -> claimTask(req));

            row.getChildren().addAll(infoBox, urgencyBadge, claimBtn);
            availableTasksContainer.getChildren().add(row);
        }
    }

    private void claimTask(Aid_Request request) {
        if (currentUserId <= 0) return;
        final int userId = currentUserId;

        CompletableFuture.runAsync(() -> claimTaskTransaction(request.getRequestId(), request.getCategory(), userId), BACKGROUND_EXECUTOR)
                .thenRun(() -> Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Task Claimed", "You have successfully claimed this task!");
                    loadAvailableTasks();
                    loadActiveTasks();
                }))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Could not claim task", error);
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    String detail = cause.getMessage() == null ? "Unknown claim error" : cause.getMessage();
                    String message = detail.toLowerCase(Locale.ROOT).contains("no longer pending")
                            ? "This request has already been claimed or its status has changed."
                            : detail.toLowerCase(Locale.ROOT).contains("no nearby")
                            ? "Your available resource is not close enough to this request."
                            : detail.toLowerCase(Locale.ROOT).contains("unreserved")
                            ? "Your resource quantity is already reserved for another assignment."
                            : "The task could not be claimed: " + detail;
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Task Unavailable", message));
                    return null;
                });
    }

    private void claimTaskTransaction(int requestId, String category, int userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String requestStatus;
                try (PreparedStatement requestLock = conn.prepareStatement(
                        "SELECT status FROM AID_REQUEST WHERE request_id = ? FOR UPDATE")) {
                    requestLock.setInt(1, requestId);
                    try (ResultSet rs = requestLock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Request not found");
                        requestStatus = rs.getString("status");
                    }
                }
                if (!"pending".equalsIgnoreCase(requestStatus)) {
                    throw new SQLException("Request is no longer pending");
                }

                int resourceId = -1;
                try (PreparedStatement resourceLock = conn.prepareStatement(
                        "SELECT resource.resource_id, resource.quantity, " +
                                "COALESCE((SELECT SUM(reserved.assigned_quantity) FROM ASSIGNMENT reserved " +
                                "WHERE reserved.resource_id=resource.resource_id " +
                                "AND LOWER(reserved.status) IN ('assigned','on the way')),0) reserved_quantity, " +
                                "resLoc.latitude resource_latitude, resLoc.longitude resource_longitude, " +
                                "resLoc.city resource_city, resLoc.district resource_district, " +
                                "reqLoc.latitude request_latitude, reqLoc.longitude request_longitude, " +
                                "reqLoc.city request_city, reqLoc.district request_district " +
                                "FROM RESOURCE resource " +
                                "JOIN LOCATION resLoc ON resource.location_id=resLoc.location_id " +
                                "JOIN AID_REQUEST req ON req.request_id=? " +
                                "JOIN LOCATION reqLoc ON req.location_id=reqLoc.location_id " +
                                "WHERE resource.user_id=? AND LOWER(resource.category)=LOWER(?) " +
                                "AND resource.quantity > 0 FOR UPDATE")) {
                    resourceLock.setInt(1, requestId);
                    resourceLock.setInt(2, userId);
                    resourceLock.setString(3, category);
                    try (ResultSet rs = resourceLock.executeQuery()) {
                        double nearestDistance = Double.MAX_VALUE;
                        while (rs.next()) {
                            int quantity = rs.getInt("quantity");
                            int reserved = rs.getInt("reserved_quantity");
                            if (quantity - reserved < 1) continue;
                            Double requestLatitude = nullableDouble(rs, "request_latitude");
                            Double requestLongitude = nullableDouble(rs, "request_longitude");
                            Double resourceLatitude = nullableDouble(rs, "resource_latitude");
                            Double resourceLongitude = nullableDouble(rs, "resource_longitude");
                            double distance = distanceKm(requestLatitude, requestLongitude,
                                    resourceLatitude, resourceLongitude);
                            boolean sameRegion = sameRegion(rs.getString("request_city"),
                                    rs.getString("request_district"), rs.getString("resource_city"),
                                    rs.getString("resource_district"));
                            double effectiveDistance = distance == Double.MAX_VALUE && sameRegion ? 0 : distance;
                            if ((effectiveDistance <= VOLUNTEER_RADIUS_KM || sameRegion)
                                    && effectiveDistance < nearestDistance) {
                                nearestDistance = effectiveDistance;
                                resourceId = rs.getInt("resource_id");
                            }
                        }
                        if (resourceId < 0) throw new SQLException("No nearby unreserved resource available");
                    }
                }

                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO ASSIGNMENT (request_id, resource_id, assigned_quantity, status) " +
                                "VALUES (?, ?, 1, 'Assigned')")) {
                    insert.setInt(1, requestId);
                    insert.setInt(2, resourceId);
                    insert.executeUpdate();
                }

                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE AID_REQUEST SET status = 'In Progress' WHERE request_id = ? AND LOWER(status) = 'pending'")) {
                    update.setInt(1, requestId);
                    if (update.executeUpdate() != 1) throw new SQLException("Request status changed");
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

    // =========================================================================
    // 3. MY ACTIVE TASKS (KART GÖRÜNÜMÜ + DURUM GÜNCELLEME)
    // =========================================================================

    private void loadActiveTasks() {
        if (currentUserId <= 0) return;
        final int userId = currentUserId;
        CompletableFuture.supplyAsync(() -> fetchActiveTasks(userId), BACKGROUND_EXECUTOR)
                .thenAccept(tasks -> Platform.runLater(() -> {
                    activeTasksContainer.getChildren().clear();
                    for (ActiveTask task : tasks) {
                        activeTasksContainer.getChildren().add(createActiveTaskCard(task.assignmentId(),
                                task.requestId(), task.category(), task.description(), task.status(),
                                task.quantity(), task.city(), task.district()));
                    }
                    activeTaskCountBadge.setText(tasks.size() + " IN PROGRESS");
                }))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Could not load active tasks", error);
                    return null;
                });
    }

    private java.util.List<ActiveTask> fetchActiveTasks(int userId) {
        java.util.List<ActiveTask> tasks = new java.util.ArrayList<>();
        String query = "SELECT a.assignment_id, req.request_id, req.category, req.description, " +
                "a.status, a.assigned_quantity, loc.city, loc.district FROM ASSIGNMENT a " +
                "JOIN AID_REQUEST req ON a.request_id = req.request_id " +
                "JOIN RESOURCE res ON a.resource_id = res.resource_id " +
                "LEFT JOIN LOCATION loc ON req.location_id = loc.location_id " +
                "WHERE res.user_id = ? AND LOWER(a.status) IN ('assigned', 'on the way')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(new ActiveTask(rs.getInt("assignment_id"), rs.getInt("request_id"),
                            rs.getString("category"), rs.getString("description"), rs.getString("status"),
                            rs.getInt("assigned_quantity"), rs.getString("city"), rs.getString("district")));
                }
            }
            return tasks;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private VBox createActiveTaskCard(int assignId, int reqId, String category, String description,
                                      String status, int assignedQuantity, String city, String district) {
        VBox card = new VBox(12);
        card.getStyleClass().add("active-task-card");
        card.setPrefWidth(360);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label idLabel = new Label("REQ-" + reqId);
        idLabel.getStyleClass().add("task-req-id");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(safeUpper(status));
        statusBadge.getStyleClass().add("urgency-badge-high");

        topRow.getChildren().addAll(idLabel, spacer, statusBadge);

        Label titleLabel = new Label(safeUpper(category) + " Delivery (" + assignedQuantity + " unit)");
        titleLabel.getStyleClass().add("task-title");

        Label descLabel = new Label(safeText(description));
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("task-meta");

        String locationDisplay = (city != null && district != null) ? "📍 " + district + ", " + city : "📍 Location Not Provided";
        Label locLabel = new Label(locationDisplay);
        locLabel.getStyleClass().add("task-meta");

        Button actionBtn = new Button();
        actionBtn.setMaxWidth(Double.MAX_VALUE);

        if ("Assigned".equalsIgnoreCase(status)) {
            actionBtn.setText("Start Route");
            actionBtn.getStyleClass().add("secondary-action-btn");
            actionBtn.setOnAction(e -> updateAssignmentStatus(assignId, "On the Way"));
        } else if ("On the Way".equalsIgnoreCase(status)) {
            actionBtn.setText("Mark Delivered");
            actionBtn.getStyleClass().add("indigo-gradient-btn");
            actionBtn.setOnAction(e -> completeDelivery(assignId));
        }

        card.getChildren().addAll(topRow, titleLabel, descLabel, locLabel, actionBtn);
        return card;
    }

    private void updateAssignmentStatus(int assignmentId, String newStatus) {
        CompletableFuture.runAsync(() -> {
            String updateAssign = "UPDATE ASSIGNMENT a JOIN RESOURCE r ON r.resource_id = a.resource_id " +
                    "SET a.status = ? WHERE a.assignment_id = ? AND r.user_id = ? " +
                    "AND LOWER(a.status) = 'assigned'";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(updateAssign)) {
                ps.setString(1, newStatus);
                ps.setInt(2, assignmentId);
                ps.setInt(3, currentUserId);
                if (ps.executeUpdate() != 1) throw new SQLException("Assignment is no longer available");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, BACKGROUND_EXECUTOR).thenRun(() -> Platform.runLater(this::loadActiveTasks))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Could not update assignment status", error);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Update Failed",
                            "The assignment status could not be updated."));
                    return null;
                });
    }

    private void completeDelivery(int assignmentId) {
        CompletableFuture.runAsync(() -> completeDeliveryTransaction(assignmentId, currentUserId), BACKGROUND_EXECUTOR)
                .thenRun(() -> Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Delivery Completed", "The assignment was marked as delivered.");
                    loadActiveTasks();
                    loadMyResources();
                }))
                .exceptionally(error -> {
                    LOGGER.log(Level.WARNING, "Could not complete delivery", error);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Delivery Failed",
                            "The assignment was not delivered because the required resource is unavailable."));
                    return null;
                });
    }

    private void completeDeliveryTransaction(int assignmentId, int userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int resourceId;
                int quantity;
                int requestId;
                try (PreparedStatement statement = conn.prepareStatement(
                        "SELECT a.resource_id, a.assigned_quantity, a.request_id " +
                                "FROM ASSIGNMENT a JOIN RESOURCE r ON r.resource_id = a.resource_id " +
                        "WHERE a.assignment_id = ? AND r.user_id = ? AND LOWER(a.status) = 'on the way' FOR UPDATE")) {
                    statement.setInt(1, assignmentId);
                    statement.setInt(2, userId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Assignment not found or not owned by user");
                        resourceId = rs.getInt("resource_id");
                        quantity = Math.max(1, rs.getInt("assigned_quantity"));
                        requestId = rs.getInt("request_id");
                    }
                }

                try (PreparedStatement updateResource = conn.prepareStatement(
                        "UPDATE RESOURCE SET quantity = quantity - ? " +
                                "WHERE resource_id = ? AND user_id = ? AND quantity >= ?")) {
                    updateResource.setInt(1, quantity);
                    updateResource.setInt(2, resourceId);
                    updateResource.setInt(3, userId);
                    updateResource.setInt(4, quantity);
                    if (updateResource.executeUpdate() != 1) {
                        throw new SQLException("Insufficient resource quantity");
                    }
                }

                try (PreparedStatement updateAssignment = conn.prepareStatement(
                        "UPDATE ASSIGNMENT SET status = 'Delivered' WHERE assignment_id = ? AND LOWER(status) = 'on the way'")) {
                    updateAssignment.setInt(1, assignmentId);
                    if (updateAssignment.executeUpdate() != 1) throw new SQLException("Assignment status changed");
                }

                try (PreparedStatement updateRequest = conn.prepareStatement(
                        "UPDATE AID_REQUEST SET status = 'Fulfilled' WHERE request_id = ?")) {
                    updateRequest.setInt(1, requestId);
                    updateRequest.executeUpdate();
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
        public void setLocation(double latitude, double longitude) {
            final long version = ++locationRequestVersion;
            locationLookupInProgress = true;
            Platform.runLater(() -> volunteerMapStatusLabel.setText("DETERMINING LOCATION..."));

            CompletableFuture.supplyAsync(() -> fetchLocationName(latitude, longitude), BACKGROUND_EXECUTOR)
                    .thenAccept(location -> Platform.runLater(() -> {
                        if (version != locationRequestVersion) return;
                        selectedLatitude = latitude;
                        selectedLongitude = longitude;
                        selectedCity = location[0];
                        selectedDistrict = location[1];
                        locationLookupInProgress = false;
                        volunteerMapStatusLabel.setText("SELECTED: " + location[1].toUpperCase(Locale.ROOT)
                                + ", " + location[0].toUpperCase(Locale.ROOT));
                    }))
                    .exceptionally(error -> {
                        LOGGER.log(Level.WARNING, "Could not determine volunteer location", error);
                        Platform.runLater(() -> {
                            if (version != locationRequestVersion) return;
                            selectedLatitude = latitude;
                            selectedLongitude = longitude;
                            selectedCity = "Unknown";
                            selectedDistrict = "Unknown";
                            locationLookupInProgress = false;
                            volunteerMapStatusLabel.setText("LOCATION DETAILS UNAVAILABLE");
                        });
                        return null;
                    });
        }
    }

    private String[] fetchLocationName(double latitude, double longitude) {
        java.net.HttpURLConnection connection = null;
        try {
            java.net.URL url = new java.net.URL(
                    "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" +
                            latitude + "&longitude=" + longitude + "&localityLanguage=tr");
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "CrisasterHub/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != 200) throw new IOException("Reverse-geocoding HTTP error");

            StringBuilder body = new StringBuilder();
            try (java.util.Scanner scanner = new java.util.Scanner(connection.getInputStream(), "UTF-8")) {
                while (scanner.hasNextLine()) body.append(scanner.nextLine());
            }
            JsonObject object = JsonParser.parseString(body.toString()).getAsJsonObject();
            String city = jsonValue(object, "city");
            if ("Unknown".equals(city)) city = jsonValue(object, "principalSubdivision");
            return new String[]{city, jsonValue(object, "locality")};
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String jsonValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.isJsonPrimitive()
                ? value.getAsString() : "Unknown";
    }

    private String createMapHtml() {
        return "<!DOCTYPE html><html><head>" +
                "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\">" +
                "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>" +
                "<style>html,body,#map{width:100%;height:100%;margin:0;background:#081328}</style>" +
                "</head><body><div id=\"map\"></div><script>" +
                "var map=L.map('map').setView([39.7767,30.5206],13);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap'}).addTo(map);" +
                "var marker;map.on('click',function(e){if(marker)map.removeLayer(marker);" +
                "marker=L.marker([e.latlng.lat,e.latlng.lng]).addTo(map);" +
                "if(typeof javaConnector!=='undefined')javaConnector.setLocation(e.latlng.lat,e.latlng.lng);});" +
                "</script></body></html>";
    }

    // =========================================================================
    // 4. LOGOUT & HELPERS
    // =========================================================================

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


    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeUpper(String value) {
        return safeText(value).toUpperCase(Locale.ROOT);
    }

    private static double configuredRadiusKm() {
        String configured = System.getProperty("VOLUNTEER_RADIUS_KM");
        if (configured == null || configured.isBlank()) configured = System.getenv("VOLUNTEER_RADIUS_KM");
        try {
            double radius = Double.parseDouble(configured == null ? "" : configured);
            return radius > 0 && radius <= 1000 ? radius : DEFAULT_VOLUNTEER_RADIUS_KM;
        } catch (NumberFormatException e) {
            return DEFAULT_VOLUNTEER_RADIUS_KM;
        }
    }
}
