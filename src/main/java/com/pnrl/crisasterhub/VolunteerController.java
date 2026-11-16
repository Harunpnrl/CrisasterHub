package com.pnrl.crisasterhub;

import com.pnrl.crisasterhub.dbclasses.Aid_Request;
import com.pnrl.crisasterhub.dbclasses.Resource;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class VolunteerController {

    // Sol Form Bileşenleri
    @FXML private ComboBox<String> resourceCategoryCombo;
    @FXML private TextField resourceQuantityField;
    @FXML private TextArea resourceDescriptionArea;

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

    @FXML
    public void initialize() {
        ObservableList<String> categories = FXCollections.observableArrayList(
                "Food", "Water", "Medicine", "Shelter", "Clothing", "Other"
        );
        resourceCategoryCombo.setItems(categories);

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
                    return task.getDescription() != null && task.getDescription().toLowerCase().contains(filter);
                });
                renderAvailableTaskCards();
            });
        }

        System.out.println("Volunteer Portal initialized.");
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
        String categoryUi = resourceCategoryCombo.getValue();
        String quantityStr = resourceQuantityField.getText();
        String description = resourceDescriptionArea.getText();

        if (categoryUi == null || quantityStr == null || quantityStr.isEmpty() || description == null || description.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Incomplete Form", "Please fill out all fields before registering a resource.");
            return;
        }

        int quantity = Integer.parseInt(quantityStr);
        if (quantity <= 0) {
            showAlert(Alert.AlertType.WARNING, "Invalid Quantity", "Quantity must be greater than 0.");
            return;
        }

        String dbCategory = categoryUi.toLowerCase();
        String insertQuery = "INSERT INTO RESOURCE (user_id, location_id, category, quantity, description) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertQuery)) {

            ps.setInt(1, currentUserId);
            ps.setInt(2, 1);
            ps.setString(3, dbCategory);
            ps.setInt(4, quantity);
            ps.setString(5, description.trim());

            if (ps.executeUpdate() > 0) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Resource successfully registered into the network.");
                resourceCategoryCombo.setValue(null);
                resourceQuantityField.clear();
                resourceDescriptionArea.clear();

                loadMyResources();
                loadAvailableTasks();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save the resource. Please try again.");
        }
    }

    private void loadMyResources() {
        ObservableList<ResourceItem> resourceList = FXCollections.observableArrayList();
        String query = "SELECT resource_id, category, quantity, description FROM RESOURCE WHERE user_id = ? AND quantity > 0 ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setInt(1, currentUserId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String rawCategory = rs.getString("category");
                String displayCategory = rawCategory.substring(0, 1).toUpperCase() + rawCategory.substring(1);

                resourceList.add(new ResourceItem(
                        rs.getInt("resource_id"),
                        displayCategory,
                        rs.getInt("quantity"),
                        rs.getString("description")
                ));
            }

            resourcesTable.setItems(resourceList);

            int count = resourceList.size();
            resourceCountBadge.setText(count + " LISTED");
            boolean isEmpty = count == 0;
            emptyResourcesState.setVisible(isEmpty);
            emptyResourcesState.setManaged(isEmpty);

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Failed to load resources for user: " + currentUserId);
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
        TextInputDialog dialog = new TextInputDialog(String.valueOf(item.getQuantity()));
        dialog.setTitle("Edit Resource Quantity");
        dialog.setHeaderText("Update quantity for " + item.getCategory());
        dialog.setContentText("New Quantity:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(qtyStr -> {
            if (!qtyStr.matches("\\d+")) {
                showAlert(Alert.AlertType.WARNING, "Invalid Input", "Please enter a valid number.");
                return;
            }
            int newQty = Integer.parseInt(qtyStr);
            String sql = "UPDATE RESOURCE SET quantity = ? WHERE resource_id = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, newQty);
                ps.setInt(2, item.getResourceId());
                ps.executeUpdate();

                loadMyResources();
                loadAvailableTasks();
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Database Error", "Could not update resource quantity.");
            }
        });
    }

    private void handleDeleteResource(ResourceItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Resource");
        confirm.setHeaderText("Remove " + item.getCategory() + "?");
        confirm.setContentText("Are you sure you want to delete this resource from your inventory?");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.OK) {
            String sql = "DELETE FROM RESOURCE WHERE resource_id = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, item.getResourceId());
                ps.executeUpdate();

                loadMyResources();
                loadAvailableTasks();
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Database Error", "Could not delete resource (it may be linked to an active assignment).");
            }
        }
    }

    // =========================================================================
    // 2. AVAILABLE TASKS (KART LİSTESİ + KATEGORİ FİLTRESİ + ARAMA)
    // =========================================================================

    private void loadAvailableTasks() {
        allAvailableTasks.clear();

        String query = "SELECT request_id, category, description, urgency " +
                "FROM AID_REQUEST " +
                "WHERE status = 'Pending' " +
                "AND LOWER(category) IN (" +
                "    SELECT LOWER(category) FROM RESOURCE WHERE user_id = ? AND quantity > 0" +
                ") " +
                "ORDER BY FIELD(urgency, 'HIGH', 'MEDIUM', 'LOW'), created_at ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setInt(1, currentUserId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                allAvailableTasks.add(new Aid_Request(
                        rs.getInt("request_id"),
                        rs.getString("category"),
                        rs.getString("urgency"),
                        rs.getString("description")
                ));
            }

            renderAvailableTaskCards();

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Failed to load available tasks.");
        }
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

            Label titleLbl = new Label("REQ-" + req.getRequestId() + " • " + req.getCategory().toUpperCase() + " Request");
            titleLbl.getStyleClass().add("task-title");

            Label descLbl = new Label(req.getDescription());
            descLbl.setWrapText(true);
            descLbl.getStyleClass().add("task-meta");

            infoBox.getChildren().addAll(titleLbl, descLbl);

            Label urgencyBadge = new Label(req.getUrgency().toUpperCase());
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
        String findResourceQuery = "SELECT resource_id FROM RESOURCE WHERE user_id = ? AND LOWER(category) = LOWER(?) AND quantity > 0 LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection()) {
            int matchedResourceId = -1;
            try (PreparedStatement psFind = conn.prepareStatement(findResourceQuery)) {
                psFind.setInt(1, currentUserId);
                psFind.setString(2, request.getCategory());
                ResultSet rs = psFind.executeQuery();
                if (rs.next()) {
                    matchedResourceId = rs.getInt("resource_id");
                }
            }

            if (matchedResourceId == -1) {
                showAlert(Alert.AlertType.WARNING, "Resource Missing",
                        "You cannot claim this task because you do not have any '" + request.getCategory() + "' registered in your resources.");
                return;
            }

            conn.setAutoCommit(false);

            try {
                String insertAssignment = "INSERT INTO ASSIGNMENT (request_id, resource_id, status) VALUES (?, ?, 'Assigned')";
                try (PreparedStatement psAssign = conn.prepareStatement(insertAssignment)) {
                    psAssign.setInt(1, request.getRequestId());
                    psAssign.setInt(2, matchedResourceId);
                    psAssign.executeUpdate();
                }

                String updateRequest = "UPDATE AID_REQUEST SET status = 'In Progress' WHERE request_id = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateRequest)) {
                    psUpdate.setInt(1, request.getRequestId());
                    psUpdate.executeUpdate();
                }

                conn.commit();

                showAlert(Alert.AlertType.INFORMATION, "Task Claimed", "You have successfully claimed this task!");
                loadAvailableTasks();
                loadActiveTasks();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "An error occurred while claiming the task.");
        }
    }

    // =========================================================================
    // 3. MY ACTIVE TASKS (KART GÖRÜNÜMÜ + DURUM GÜNCELLEME)
    // =========================================================================

    private void loadActiveTasks() {
        activeTasksContainer.getChildren().clear();
        int count = 0;

        String query = "SELECT a.assignment_id, req.request_id, req.category, req.description, a.status, loc.city, loc.district " +
                "FROM ASSIGNMENT a " +
                "JOIN AID_REQUEST req ON a.request_id = req.request_id " +
                "JOIN RESOURCE res ON a.resource_id = res.resource_id " +
                "LEFT JOIN LOCATION loc ON req.location_id = loc.location_id " +
                "WHERE res.user_id = ? AND a.status IN ('Assigned', 'On the Way')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setInt(1, currentUserId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                count++;
                int assignId = rs.getInt("assignment_id");
                int reqId = rs.getInt("request_id");
                String cat = rs.getString("category");
                String desc = rs.getString("description");
                String status = rs.getString("status");
                String city = rs.getString("city");
                String district = rs.getString("district");

                VBox card = createActiveTaskCard(assignId, reqId, cat, desc, status, city, district);
                activeTasksContainer.getChildren().add(card);
            }

            activeTaskCountBadge.setText(count + " IN PROGRESS");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private VBox createActiveTaskCard(int assignId, int reqId, String category, String description, String status, String city, String district) {
        VBox card = new VBox(12);
        card.getStyleClass().add("active-task-card");
        card.setPrefWidth(360);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label idLabel = new Label("REQ-" + reqId);
        idLabel.getStyleClass().add("task-req-id");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(status.toUpperCase());
        statusBadge.getStyleClass().add("urgency-badge-high");

        topRow.getChildren().addAll(idLabel, spacer, statusBadge);

        Label titleLabel = new Label(category.toUpperCase() + " Delivery");
        titleLabel.getStyleClass().add("task-title");

        Label descLabel = new Label(description);
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
            actionBtn.setOnAction(e -> {
                updateAssignmentStatus(assignId, "Delivered");
                deductVolunteerResource(currentUserId, category, 1);
                loadActiveTasks();
                loadMyResources();
            });
        }

        card.getChildren().addAll(topRow, titleLabel, descLabel, locLabel, actionBtn);
        return card;
    }

    private void updateAssignmentStatus(int assignmentId, String newStatus) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String updateAssign = "UPDATE ASSIGNMENT SET status = ? WHERE assignment_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateAssign)) {
                ps.setString(1, newStatus);
                ps.setInt(2, assignmentId);
                ps.executeUpdate();
            }

            if ("Delivered".equalsIgnoreCase(newStatus)) {
                String updateReq = "UPDATE AID_REQUEST SET status = 'Fulfilled' WHERE request_id = (SELECT request_id FROM ASSIGNMENT WHERE assignment_id = ?)";
                try (PreparedStatement psReq = conn.prepareStatement(updateReq)) {
                    psReq.setInt(1, assignmentId);
                    psReq.executeUpdate();
                }
            }
            loadActiveTasks();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deductVolunteerResource(int userId, String category, int amountToDeduct) {
        String query = "UPDATE RESOURCE SET quantity = quantity - ? " +
                "WHERE user_id = ? AND UPPER(category) = UPPER(?) AND quantity >= ? " +
                "LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, amountToDeduct);
            pstmt.setInt(2, userId);
            pstmt.setString(3, category);
            pstmt.setInt(4, amountToDeduct);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Successfully deducted " + amountToDeduct + " unit(s) of " + category + ".");
            } else {
                System.out.println("Warning: Not enough " + category + " found in inventory to deduct!");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 4. LOGOUT & HELPERS
    // =========================================================================

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


    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
