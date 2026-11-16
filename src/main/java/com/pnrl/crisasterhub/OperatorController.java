package com.pnrl.crisasterhub;

import com.pnrl.crisasterhub.dbclasses.Aid_Request;
import com.pnrl.crisasterhub.dbclasses.Assignment;
import com.pnrl.crisasterhub.dbclasses.Resource;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class OperatorController {

    @FXML
    private TextField searchField;
    @FXML
    private TextField resourceSearchField;
    @FXML
    private TableView<Aid_Request> aidRequestsTable;
    @FXML
    private TableColumn<Aid_Request, String> colReqId;
    @FXML
    private TableColumn<Aid_Request, String> colUserId;
    @FXML
    private TableColumn<Aid_Request, String> colLocationId;
    @FXML
    private TableColumn<Aid_Request, String> colCategory;
    @FXML
    private TableColumn<Aid_Request, String> colUrgency;
    @FXML
    private TableColumn<Aid_Request, String> colDescription;
    @FXML
    private TableColumn<Aid_Request, String> colStatus;
    @FXML
    private TableColumn<Aid_Request, String> colCreatedAt;
    @FXML
    private TableColumn<Aid_Request, Void> colActions;
    // --- Resource Table Variables ---
    @FXML
    private TableView<Resource> resourceTable;
    @FXML
    private TableColumn<Resource, String> colResId;
    @FXML
    private TableColumn<Resource, String> colResUserId;
    @FXML
    private TableColumn<Resource, String> colResLocationId;
    @FXML
    private TableColumn<Resource, String> colResCategory;
    @FXML
    private TableColumn<Resource, String> colResQty;
    @FXML
    private TableColumn<Resource, String> colResDesc;
    @FXML
    private TableColumn<Resource, String> colResCreatedAt;
    @FXML
    private TableColumn<Resource, Void> colResActions;
    // --- Assignment Table Variables ---
    @FXML
    private TableView<Assignment> assignmentTable;
    @FXML
    private TableColumn<Assignment, String> colAsnId;
    @FXML
    private TableColumn<Assignment, String> colAsnReqId;
    @FXML
    private TableColumn<Assignment, String> colAsnResId;
    @FXML
    private TableColumn<Assignment, String> colAsnQty;
    @FXML
    private TableColumn<Assignment, String> colAsnStatus;
    @FXML
    private TableColumn<Assignment, String> colAsnAssignedAt;
    @FXML
    private TableColumn<Assignment, String> colAsnUpdatedAt;
    @FXML
    private TableColumn<Assignment, Void> colAsnActions;
    // --- Dashboard Card Variables ---
    @FXML
    private Label foodStockLabel;
    @FXML
    private ProgressBar foodProgressBar;

    @FXML
    private Label medStockLabel;
    @FXML
    private ProgressBar medProgressBar;

    @FXML
    private Label shelterStockLabel;
    @FXML
    private ProgressBar shelterProgressBar;

    @FXML
    private Label clothingStockLabel;
    @FXML
    private ProgressBar clothingProgressBar;

    @FXML
    private Label foodBadge;
    @FXML
    private Label medBadge;
    @FXML
    private Label shelterBadge;
    @FXML
    private Label clothingBadge;
    @FXML
    private TextField assignSearchField;

    private ObservableList<Assignment> assignmentList = FXCollections.observableArrayList();

    private ObservableList<Resource> resourceList = FXCollections.observableArrayList();

    private ObservableList<Aid_Request> aidRequestList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // ══════════════════════════════════════════════════════════
        // 1. AID REQUESTS SEARCH BAR
        // ══════════════════════════════════════════════════════════
        javafx.collections.transformation.FilteredList<Aid_Request> filteredRequests = new javafx.collections.transformation.FilteredList<>(aidRequestList, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredRequests.setPredicate(req -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();

                return req.getCategory().toLowerCase().contains(lowerCaseFilter) || req.getUrgency().toLowerCase().contains(lowerCaseFilter) || req.getDescription().toLowerCase().contains(lowerCaseFilter) || req.getStatus().toLowerCase().contains(lowerCaseFilter);
            });
        });

        javafx.collections.transformation.SortedList<Aid_Request> sortedRequests = new javafx.collections.transformation.SortedList<>(filteredRequests);
        sortedRequests.comparatorProperty().bind(aidRequestsTable.comparatorProperty());
        aidRequestsTable.setItems(sortedRequests);


        // ══════════════════════════════════════════════════════════
        // 2. RESOURCE INVENTORY SEARCH BAR
        // ══════════════════════════════════════════════════════════
        javafx.collections.transformation.FilteredList<Resource> filteredResources = new javafx.collections.transformation.FilteredList<>(resourceList, p -> true);

        resourceSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredResources.setPredicate(res -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();

                return res.getCategory().toLowerCase().contains(lowerCaseFilter) || res.getDescription().toLowerCase().contains(lowerCaseFilter);
            });
        });

        javafx.collections.transformation.SortedList<Resource> sortedResources = new javafx.collections.transformation.SortedList<>(filteredResources);
        sortedResources.comparatorProperty().bind(resourceTable.comparatorProperty());
        resourceTable.setItems(sortedResources);

        colReqId.setCellValueFactory(cellData -> new SimpleStringProperty("#REQ-" + cellData.getValue().getRequestId()));
        colUserId.setCellValueFactory(cellData -> new SimpleStringProperty("USR-" + cellData.getValue().getUserId()));
        colLocationId.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLocationName()));
        colCategory.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCategory().toUpperCase()));
        colUrgency.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUrgency().toUpperCase()));
        colDescription.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDescription()));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().toUpperCase()));
        colCreatedAt.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt().toString()));

        colResId.setCellValueFactory(cellData -> new SimpleStringProperty("#RES-" + cellData.getValue().getResourceId()));
        colResUserId.setCellValueFactory(cellData -> new SimpleStringProperty("USR-" + cellData.getValue().getUserId()));
        colResLocationId.setCellValueFactory(cellData -> new SimpleStringProperty("LOC-" + cellData.getValue().getLocationId()));
        colResCategory.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCategory().toUpperCase()));
        colResQty.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getQuantity() + " Units"));
        colResDesc.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDescription()));

        colResCreatedAt.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt().toString()));

        colAsnId.setCellValueFactory(cellData -> new SimpleStringProperty("#ASN-" + cellData.getValue().getAssignmentId()));
        colAsnReqId.setCellValueFactory(cellData -> new SimpleStringProperty("#REQ-" + cellData.getValue().getRequestId()));
        colAsnResId.setCellValueFactory(cellData -> new SimpleStringProperty("#RES-" + cellData.getValue().getResourceId()));
        colAsnQty.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAssignedQuantity() + " Units"));
        colAsnStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().toUpperCase()));
        colAsnAssignedAt.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAssignedAt().toString()));

        colAsnUpdatedAt.setCellValueFactory(cellData -> {
            if (cellData.getValue().getUpdatedAt() != null) {
                return new SimpleStringProperty(cellData.getValue().getUpdatedAt().toString());
            }
            return new SimpleStringProperty("-");
        });

        setupAssignmentActionButtons();
        loadAssignments();

        loadResources();
        loadAssignments();

        updateDashboardStats();

        setupResourceActionButtons();
        loadResources();

        setupActionButtons();

        loadAidRequests();

        System.out.println("Admin Command Panel initialized.");
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
            System.out.println("Admin logged out successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "System Error", "Could not return to the login screen.");
        }
    }

    private void loadAidRequests() {
        aidRequestList.clear();
        String query = "SELECT a.*, CONCAT(l.city, ', ', l.district) AS full_address " + "FROM AID_REQUEST a " + "LEFT JOIN LOCATION l ON a.location_id = l.location_id " + "ORDER BY a.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Aid_Request req = new Aid_Request(rs.getInt("request_id"), rs.getInt("user_id"), rs.getInt("location_id"), rs.getString("full_address"), rs.getString("category"), rs.getString("urgency"), rs.getString("description"), rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime());
                aidRequestList.add(req);
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupActionButtons() {
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnAssign = new Button("Assign");

            {
                btnAssign.getStyleClass().add("btn-table-primary");

                btnAssign.setOnAction(event -> {
                    Aid_Request request = getTableView().getItems().get(getIndex());
                    openAssignmentDialog(request);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // HBox yerine doğrudan sadece butonu gösteriyoruz
                    setGraphic(btnAssign);
                }
            }
        });
    }

    private void loadResources() {
        resourceList.clear();
        String query = "SELECT * FROM RESOURCE ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Resource res = new Resource(rs.getInt("resource_id"), rs.getInt("user_id"), rs.getInt("location_id"), rs.getString("category"), rs.getInt("quantity"), rs.getString("description"), rs.getTimestamp("created_at").toLocalDateTime());
                resourceList.add(res);
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupResourceActionButtons() {
        colResActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Delete");
            private final HBox pane = new HBox(6, btnEdit, btnDelete);

            {
                btnEdit.getStyleClass().add("btn-table-primary");
                btnDelete.getStyleClass().add("btn-table-danger");

                btnEdit.setOnAction(event -> {
                    Resource res = getTableView().getItems().get(getIndex());
                    openEditResourceDialog(res);
                });

                btnDelete.setOnAction(event -> {
                    Resource res = getTableView().getItems().get(getIndex());
                    System.out.println("Deleting Resource RES-" + res.getResourceId());
                    deleteResourceFromDatabase(res.getResourceId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
    }

    private void loadAssignments() {
        assignmentList.clear();
        String query = "SELECT * FROM ASSIGNMENT ORDER BY assigned_at DESC";

        try (Connection conn = DatabaseConnection.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Assignment asn = new Assignment(rs.getInt("assignment_id"), rs.getInt("request_id"), rs.getInt("resource_id"), rs.getInt("assigned_quantity"), rs.getString("status"), rs.getTimestamp("assigned_at").toLocalDateTime(), rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
                assignmentList.add(asn);
            }
            assignmentTable.setItems(assignmentList);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupAssignmentActionButtons() {
        colAsnActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnUpdate = new Button("Update");
            private final Button btnCancel = new Button("Cancel");
            private final HBox pane = new HBox(6, btnUpdate, btnCancel);

            {
                btnUpdate.getStyleClass().add("btn-table-primary");
                btnCancel.getStyleClass().add("btn-table-danger");

                btnUpdate.setOnAction(event -> {
                    Assignment asn = getTableView().getItems().get(getIndex());
                    openUpdateAssignmentDialog(asn);
                });

                btnCancel.setOnAction(event -> {
                    Assignment asn = getTableView().getItems().get(getIndex());
                    System.out.println("Canceling Assignment ASN-" + asn.getAssignmentId());

                    // Call the new Refund transaction!
                    cancelAndRefundAssignment(asn.getAssignmentId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
    }

    private void updateDashboardStats() {
        double TARGET_FOOD_WATER = 250000.0;
        double TARGET_MEDICAL = 250000.0;
        double TARGET_SHELTER = 250000.0;
        double TARGET_CLOTHING = 250000.0;

        String query = "SELECT category, SUM(quantity) AS total_qty FROM RESOURCE GROUP BY category";

        try (Connection conn = DatabaseConnection.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {

            int totalFoodWater = 0;
            int totalMed = 0;
            int totalShelter = 0;
            int totalClothing = 0;

            while (rs.next()) {
                String cat = rs.getString("category").toLowerCase();
                int qty = rs.getInt("total_qty");

                if (cat.equals("food") || cat.equals("water")) {
                    totalFoodWater += qty;
                } else if (cat.equals("medicine") || cat.equals("medical")) {
                    totalMed += qty;
                } else if (cat.equals("shelter")) {
                    totalShelter += qty;
                } else if (cat.equals("clothing")) {
                    totalClothing += qty;
                }
            }

            foodStockLabel.setText(totalFoodWater + " Units");
            medStockLabel.setText(totalMed + " Units");
            shelterStockLabel.setText(totalShelter + " Kits");
            clothingStockLabel.setText(totalClothing + " Sets");

            foodProgressBar.setProgress(Math.min(1.0, totalFoodWater / TARGET_FOOD_WATER));
            medProgressBar.setProgress(Math.min(1.0, totalMed / TARGET_MEDICAL));
            shelterProgressBar.setProgress(Math.min(1.0, totalShelter / TARGET_SHELTER));
            clothingProgressBar.setProgress(Math.min(1.0, totalClothing / TARGET_CLOTHING));


            double foodPct = Math.min(1.0, totalFoodWater / TARGET_FOOD_WATER);
            double medPct = Math.min(1.0, totalMed / TARGET_MEDICAL);
            double shelterPct = Math.min(1.0, totalShelter / TARGET_SHELTER);
            double clothingPct = Math.min(1.0, totalClothing / TARGET_CLOTHING);

            foodProgressBar.setProgress(foodPct);
            medProgressBar.setProgress(medPct);
            shelterProgressBar.setProgress(shelterPct);
            clothingProgressBar.setProgress(clothingPct);

            updateBadge(foodBadge, foodPct);
            updateBadge(medBadge, medPct);
            updateBadge(shelterBadge, shelterPct);
            updateBadge(clothingBadge, clothingPct);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deleteResourceFromDatabase(int resourceId) {
        String query = "DELETE FROM RESOURCE WHERE resource_id = ?";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, resourceId);
            pstmt.executeUpdate();

            System.out.println("Successfully deleted from database.");


            loadResources();
            updateDashboardStats();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void openAssignmentDialog(Aid_Request request) {
        TextInputDialog resourceDialog = new TextInputDialog();
        resourceDialog.setTitle("Assign Resource");
        resourceDialog.setHeaderText("Assigning resource for Request #REQ-" + request.getRequestId() + "\nCategory Needed: " + request.getCategory());
        resourceDialog.setContentText("Enter the Resource ID (e.g., 6):");

        Optional<String> resIdResult = resourceDialog.showAndWait();
        if (resIdResult.isPresent() && !resIdResult.get().trim().isEmpty()) {

            TextInputDialog qtyDialog = new TextInputDialog();
            qtyDialog.setTitle("Assign Quantity");
            qtyDialog.setHeaderText("Resource #" + resIdResult.get() + " selected.");
            qtyDialog.setContentText("Enter quantity to assign:");

            Optional<String> qtyResult = qtyDialog.showAndWait();
            if (qtyResult.isPresent() && !qtyResult.get().trim().isEmpty()) {

                try {
                    int resourceId = Integer.parseInt(resIdResult.get().trim());
                    int quantity = Integer.parseInt(qtyResult.get().trim());

                    executeAssignmentTransaction(request.getRequestId(), resourceId, quantity);

                } catch (NumberFormatException e) {
                    System.out.println("Invalid number format entered.");
                }
            }
        }
    }

    private void executeAssignmentTransaction(int requestId, int resourceId, int quantity) {
        String insertAssignment = "INSERT INTO ASSIGNMENT (request_id, resource_id, assigned_quantity, status) VALUES (?, ?, ?, 'Assigned')";
        String updateResource = "UPDATE RESOURCE SET quantity = quantity - ? WHERE resource_id = ?";
        String updateRequest = "UPDATE AID_REQUEST SET status = 'FULFILLED' WHERE request_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {

            conn.setAutoCommit(false);

            try (PreparedStatement stmtAsn = conn.prepareStatement(insertAssignment); PreparedStatement stmtRes = conn.prepareStatement(updateResource); PreparedStatement stmtReq = conn.prepareStatement(updateRequest)) {

                stmtAsn.setInt(1, requestId);
                stmtAsn.setInt(2, resourceId);
                stmtAsn.setInt(3, quantity);
                stmtAsn.executeUpdate();

                stmtRes.setInt(1, quantity);
                stmtRes.setInt(2, resourceId);
                stmtRes.executeUpdate();

                stmtReq.setInt(1, requestId);
                stmtReq.executeUpdate();

                conn.commit();
                System.out.println("Assignment successful!");

                loadAidRequests();
                loadResources();
                loadAssignments();
                updateDashboardStats();

            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void openEditResourceDialog(Resource res) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(res.getQuantity()));
        dialog.setTitle("Edit Resource Inventory");
        dialog.setHeaderText("Editing RES-" + res.getResourceId() + " (" + res.getCategory() + ")");
        dialog.setContentText("Enter the new total quantity:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(qtyStr -> {
            try {
                int newQty = Integer.parseInt(qtyStr.trim());
                updateResourceQuantityInDatabase(res.getResourceId(), newQty);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number entered.");
            }
        });
    }

    private void updateResourceQuantityInDatabase(int resourceId, int newQuantity) {
        String query = "UPDATE RESOURCE SET quantity = ? WHERE resource_id = ?";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, newQuantity);
            pstmt.setInt(2, resourceId);
            pstmt.executeUpdate();

            loadResources();
            updateDashboardStats();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void openUpdateAssignmentDialog(Assignment asn) {
        List<String> statuses = Arrays.asList("Assigned", "On the Way", "Delivered");

        ChoiceDialog<String> dialog = new ChoiceDialog<>(asn.getStatus(), statuses);
        dialog.setTitle("Update Assignment Status");
        dialog.setHeaderText("Updating status for ASN-" + asn.getAssignmentId());
        dialog.setContentText("Select new status:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newStatus -> {
            updateAssignmentStatusInDatabase(asn.getAssignmentId(), newStatus);
        });
    }

    private void updateAssignmentStatusInDatabase(int assignmentId, String newStatus) {
        String query = "UPDATE ASSIGNMENT SET status = ? WHERE assignment_id = ?";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, newStatus);
            pstmt.setInt(2, assignmentId);
            pstmt.executeUpdate();

            loadAssignments();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void cancelAndRefundAssignment(int assignmentId) {
        String selectQuery = "SELECT resource_id, assigned_quantity FROM ASSIGNMENT WHERE assignment_id = ?";
        String updateResourceQuery = "UPDATE RESOURCE SET quantity = quantity + ? WHERE resource_id = ?";
        String deleteAssignmentQuery = "DELETE FROM ASSIGNMENT WHERE assignment_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                int resourceId = -1;
                int refundQuantity = 0;

                try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {
                    selectStmt.setInt(1, assignmentId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            resourceId = rs.getInt("resource_id");
                            refundQuantity = rs.getInt("assigned_quantity");
                        }
                    }
                }

                if (resourceId != -1) {

                    try (PreparedStatement updateStmt = conn.prepareStatement(updateResourceQuery)) {
                        updateStmt.setInt(1, refundQuantity);
                        updateStmt.setInt(2, resourceId);
                        updateStmt.executeUpdate();
                    }

                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteAssignmentQuery)) {
                        deleteStmt.setInt(1, assignmentId);
                        deleteStmt.executeUpdate();
                    }

                    conn.commit();
                    System.out.println("Assignment canceled and resources refunded!");

                    loadAssignments();
                    loadResources();
                    updateDashboardStats();

                } else {
                    conn.rollback();
                }

            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateBadge(Label badge, double percentage) {
        if (percentage >= 0.5) {
            badge.setText("Stable");
        } else if (percentage >= 0.2) {
            badge.setText("Low Stock");
        } else {
            badge.setText("Critical");
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
