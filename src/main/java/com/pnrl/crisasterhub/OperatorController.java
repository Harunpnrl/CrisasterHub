package com.pnrl.crisasterhub;

import com.pnrl.crisasterhub.dbclasses.Aid_Request;
import com.pnrl.crisasterhub.dbclasses.Assignment;
import com.pnrl.crisasterhub.dbclasses.Resource;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Operator dashboard. All mutations are validated and committed atomically. */
public class OperatorController {
    private static final Logger LOGGER = Logger.getLogger(OperatorController.class.getName());
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "operator-background-task");
        thread.setDaemon(true);
        return thread;
    });

    private static final String PENDING = "pending";
    private static final String IN_PROGRESS = "in progress";
    private static final String ASSIGNED = "assigned";
    private static final String ON_THE_WAY = "on the way";
    private static final String DELIVERED = "delivered";
    private static final String FULFILLED = "fulfilled";

    @FXML private TextField searchField;
    @FXML private TextField resourceSearchField;
    @FXML private TextField assignSearchField;
    @FXML private TableView<Aid_Request> aidRequestsTable;
    @FXML private TableColumn<Aid_Request, String> colReqId, colUserId, colLocationId, colCategory,
            colUrgency, colDescription, colStatus, colCreatedAt;
    @FXML private TableColumn<Aid_Request, Void> colActions;
    @FXML private TableView<Resource> resourceTable;
    @FXML private TableColumn<Resource, String> colResId, colResUserId, colResLocationId, colResCategory,
            colResQty, colResDesc, colResCreatedAt;
    @FXML private TableColumn<Resource, Void> colResActions;
    @FXML private TableView<Assignment> assignmentTable;
    @FXML private TableColumn<Assignment, String> colAsnId, colAsnReqId, colAsnResId, colAsnQty,
            colAsnStatus, colAsnAssignedAt, colAsnUpdatedAt;
    @FXML private TableColumn<Assignment, Void> colAsnActions;
    @FXML private Label foodStockLabel, medStockLabel, shelterStockLabel, clothingStockLabel;
    @FXML private ProgressBar foodProgressBar, medProgressBar, shelterProgressBar, clothingProgressBar;
    @FXML private Label foodStatusBadge, medStatusBadge, shelterStatusBadge, clothingStatusBadge;
    @FXML private Label foodPercentLabel, medPercentLabel, shelterPercentLabel, clothingPercentLabel;
    @FXML private Label statActiveReqsLabel, statPendingAsnLabel, statDepotsLabel;

    private final ObservableList<Assignment> assignmentList = FXCollections.observableArrayList();
    private final ObservableList<Resource> resourceList = FXCollections.observableArrayList();
    private final ObservableList<Aid_Request> aidRequestList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (!AuthService.hasActiveRole("operator")) {
            LOGGER.warning("Operator panel initialized without an active operator session");
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "An active operator session is required."));
            return;
        }
        configureRequestTable();
        configureResourceTable();
        configureAssignmentTable();
        setupActionButtons();
        setupResourceActionButtons();
        setupAssignmentActionButtons();
        loadAllData();
        LOGGER.info("Operator panel initialized");
    }

    private void configureRequestTable() {
        FilteredList<Aid_Request> filtered = new FilteredList<>(aidRequestList, item -> true);
        searchField.textProperty().addListener((obs, oldValue, value) -> filtered.setPredicate(item -> {
            String filter = lower(value);
            return filter.isBlank() || contains(item.getRequestId(), filter)
                    || contains(item.getUserId(), filter) || contains(item.getLocationName(), filter)
                    || contains(item.getCategory(), filter) || contains(item.getUrgency(), filter)
                    || contains(item.getDescription(), filter) || contains(item.getStatus(), filter);
        }));
        SortedList<Aid_Request> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(aidRequestsTable.comparatorProperty());
        aidRequestsTable.setItems(sorted);
        colReqId.setCellValueFactory(c -> text("#REQ-" + c.getValue().getRequestId()));
        colUserId.setCellValueFactory(c -> text("USR-" + c.getValue().getUserId()));
        colLocationId.setCellValueFactory(c -> text(safe(c.getValue().getLocationName())));
        colCategory.setCellValueFactory(c -> text(upper(c.getValue().getCategory())));
        colUrgency.setCellValueFactory(c -> text(upper(c.getValue().getUrgency())));
        colDescription.setCellValueFactory(c -> text(safe(c.getValue().getDescription())));
        colStatus.setCellValueFactory(c -> text(upper(c.getValue().getStatus())));
        colCreatedAt.setCellValueFactory(c -> text(format(c.getValue().getCreatedAt())));
        configureStatusCell(colUrgency, this::urgencyClass);
        configureStatusCell(colStatus, this::statusClass);
    }

    private void configureResourceTable() {
        FilteredList<Resource> filtered = new FilteredList<>(resourceList, item -> true);
        resourceSearchField.textProperty().addListener((obs, oldValue, value) -> filtered.setPredicate(item -> {
            String filter = lower(value);
            return filter.isBlank() || contains(item.getResourceId(), filter)
                    || contains(item.getUserId(), filter) || contains(item.getLocationId(), filter)
                    || contains(item.getCategory(), filter) || contains(item.getQuantity(), filter)
                    || contains(item.getDescription(), filter);
        }));
        SortedList<Resource> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(resourceTable.comparatorProperty());
        resourceTable.setItems(sorted);
        colResId.setCellValueFactory(c -> text("#RES-" + c.getValue().getResourceId()));
        colResUserId.setCellValueFactory(c -> text("USR-" + c.getValue().getUserId()));
        colResLocationId.setCellValueFactory(c -> text("LOC-" + c.getValue().getLocationId()));
        colResCategory.setCellValueFactory(c -> text(upper(c.getValue().getCategory())));
        colResQty.setCellValueFactory(c -> text(c.getValue().getQuantity() + " Units"));
        colResDesc.setCellValueFactory(c -> text(safe(c.getValue().getDescription())));
        colResCreatedAt.setCellValueFactory(c -> text(format(c.getValue().getCreatedAt())));
    }

    private void configureAssignmentTable() {
        FilteredList<Assignment> filtered = new FilteredList<>(assignmentList, item -> true);
        assignSearchField.textProperty().addListener((obs, oldValue, value) -> filtered.setPredicate(item -> {
            String filter = lower(value);
            return filter.isBlank() || contains(item.getAssignmentId(), filter)
                    || contains(item.getRequestId(), filter) || contains(item.getResourceId(), filter)
                    || contains(item.getAssignedQuantity(), filter) || contains(item.getStatus(), filter);
        }));
        SortedList<Assignment> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(assignmentTable.comparatorProperty());
        assignmentTable.setItems(sorted);
        colAsnId.setCellValueFactory(c -> text("#ASN-" + c.getValue().getAssignmentId()));
        colAsnReqId.setCellValueFactory(c -> text("#REQ-" + c.getValue().getRequestId()));
        colAsnResId.setCellValueFactory(c -> text("#RES-" + c.getValue().getResourceId()));
        colAsnQty.setCellValueFactory(c -> text(c.getValue().getAssignedQuantity() + " Units"));
        colAsnStatus.setCellValueFactory(c -> text(upper(c.getValue().getStatus())));
        colAsnAssignedAt.setCellValueFactory(c -> text(format(c.getValue().getAssignedAt())));
        colAsnUpdatedAt.setCellValueFactory(c -> text(format(c.getValue().getUpdatedAt())));
        configureStatusCell(colAsnStatus, this::statusClass);
    }

    private <T> void configureStatusCell(TableColumn<T, String> column, java.util.function.Function<String, String> style) {
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeIf(value -> value.startsWith("badge-"));
                if (!empty) getStyleClass().add(style.apply(item));
            }
        });
    }

    private void loadAllData() {
        CompletableFuture.runAsync(this::ensureAuditTable, EXECUTOR);
        CompletableFuture.allOf(
                loadRequests(), loadResources(), loadAssignments(), loadDashboard()).exceptionally(error -> {
            LOGGER.log(Level.WARNING, "Could not load operator dashboard", error);
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Load Error",
                    "Some operator data could not be loaded."));
            return null;
        });
    }

    private void ensureAuditTable() {
        String sql = "CREATE TABLE IF NOT EXISTS OPERATOR_AUDIT_LOG (" +
                "audit_id BIGINT AUTO_INCREMENT PRIMARY KEY, operator_user_id INT NOT NULL, " +
                "action VARCHAR(80) NOT NULL, entity_type VARCHAR(40) NOT NULL, entity_id INT NOT NULL, " +
                "details VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Connection c = DatabaseConnection.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Audit log table is unavailable", e);
        }
    }

    private CompletableFuture<Void> loadRequests() {
        return CompletableFuture.supplyAsync(this::fetchRequests, EXECUTOR).thenAccept(items ->
                Platform.runLater(() -> {
                    aidRequestList.setAll(items);
                    updateSummaryStats();
                }));
    }

    private CompletableFuture<Void> loadResources() {
        return CompletableFuture.supplyAsync(this::fetchResources, EXECUTOR).thenAccept(items ->
                Platform.runLater(() -> {
                    resourceList.setAll(items);
                    updateSummaryStats();
                }));
    }

    private CompletableFuture<Void> loadAssignments() {
        return CompletableFuture.supplyAsync(this::fetchAssignments, EXECUTOR).thenAccept(items ->
                Platform.runLater(() -> {
                    assignmentList.setAll(items);
                    updateSummaryStats();
                }));
    }

    private CompletableFuture<Void> loadDashboard() {
        return CompletableFuture.supplyAsync(this::fetchDashboard, EXECUTOR).thenAccept(values ->
                Platform.runLater(() -> updateDashboard(values)));
    }

    private List<Aid_Request> fetchRequests() {
        List<Aid_Request> result = new java.util.ArrayList<>();
        String query = "SELECT a.*, CONCAT(l.city, ', ', l.district) AS full_address " +
                "FROM AID_REQUEST a LEFT JOIN LOCATION l ON a.location_id=l.location_id " +
                "WHERE LOWER(a.status) <> 'fulfilled' ORDER BY a.created_at DESC";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(query);
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) result.add(new Aid_Request(rs.getInt("request_id"), rs.getInt("user_id"),
                    rs.getInt("location_id"), rs.getString("full_address"), rs.getString("category"),
                    rs.getString("urgency"), rs.getString("description"), rs.getString("status"),
                    toDate(rs.getTimestamp("created_at"))));
            return result;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private List<Resource> fetchResources() {
        List<Resource> result = new java.util.ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT * FROM RESOURCE ORDER BY created_at DESC");
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) result.add(new Resource(rs.getInt("resource_id"), rs.getInt("user_id"),
                    rs.getInt("location_id"), rs.getString("category"), rs.getInt("quantity"),
                    rs.getString("description"), toDate(rs.getTimestamp("created_at"))));
            return result;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private List<Assignment> fetchAssignments() {
        List<Assignment> result = new java.util.ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT * FROM ASSIGNMENT ORDER BY assigned_at DESC");
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) result.add(new Assignment(rs.getInt("assignment_id"), rs.getInt("request_id"),
                    rs.getInt("resource_id"), rs.getInt("assigned_quantity"), rs.getString("status"),
                    toDate(rs.getTimestamp("assigned_at")), toDate(rs.getTimestamp("updated_at"))));
            return result;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private int[] fetchDashboard() {
        int[] values = new int[4];
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT LOWER(category) category, COALESCE(SUM(quantity),0) total FROM RESOURCE GROUP BY LOWER(category)");
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                String category = rs.getString("category"); int quantity = rs.getInt("total");
                if (category.equals("food") || category.equals("water")) values[0] += quantity;
                else if (category.equals("medicine") || category.equals("medical")) values[1] += quantity;
                else if (category.equals("shelter")) values[2] += quantity;
                else if (category.equals("clothing")) values[3] += quantity;
            }
            return values;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void setupActionButtons() {
        colActions.setCellFactory(ignored -> new TableCell<>() {
            private final Button assign = new Button("Assign");
            private final Button edit = new Button("Edit");
            private final Button delete = new Button("Delete");
            private final HBox pane = new HBox(7, assign, edit, delete);
            { assign.getStyleClass().add("btn-table-primary"); edit.getStyleClass().add("btn-table-secondary"); delete.getStyleClass().add("btn-table-danger");
              assign.setOnAction(e -> {
                Aid_Request item = getTableView().getItems().get(getIndex());
                if (item != null) openAssignmentDialog(item);
              });
              edit.setOnAction(e -> openEditRequestDialog(getTableView().getItems().get(getIndex())));
              delete.setOnAction(e -> confirmDeleteRequest(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty); Aid_Request request = empty ? null : getTableView().getItems().get(getIndex());
                assign.setDisable(request == null || !PENDING.equals(lower(request.getStatus())));
                edit.setDisable(request == null);
                delete.setDisable(request == null);
                setGraphic(empty ? null : pane);
            }
        });
    }

    private void setupResourceActionButtons() {
        colResActions.setCellFactory(ignored -> new TableCell<>() {
            private final Button edit = new Button("Edit"), delete = new Button("Delete");
            private final HBox pane = new HBox(7, edit, delete);
            { edit.getStyleClass().add("btn-table-primary"); delete.getStyleClass().add("btn-table-danger");
              edit.setOnAction(e -> openEditResourceDialog(getTableView().getItems().get(getIndex())));
              delete.setOnAction(e -> confirmDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : pane); }
        });
    }

    private void setupAssignmentActionButtons() {
        colAsnActions.setCellFactory(ignored -> new TableCell<>() {
            private final Button update = new Button("Update"), cancel = new Button("Cancel");
            private final HBox pane = new HBox(7, update, cancel);
            { update.getStyleClass().add("btn-table-primary"); cancel.getStyleClass().add("btn-table-danger");
              update.setOnAction(e -> openUpdateAssignmentDialog(getTableView().getItems().get(getIndex())));
              cancel.setOnAction(e -> confirmCancel(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty);
                Assignment a = empty ? null : getTableView().getItems().get(getIndex());
                boolean closed = a == null || DELIVERED.equals(lower(a.getStatus()));
                update.setDisable(closed); cancel.setDisable(closed); setGraphic(empty ? null : pane); }
        });
    }

    private void openAssignmentDialog(Aid_Request request) {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Assign Resource");
        dialog.setHeaderText("Request #REQ-" + request.getRequestId() + " / " + upper(request.getCategory()));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField resourceId = new TextField(); resourceId.setPromptText("Resource ID");
        TextField quantity = new TextField("1"); quantity.setPromptText("Quantity");
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8, new Label("Resource ID"), resourceId,
                new Label("Quantity"), quantity); box.setPrefWidth(320); dialog.getDialogPane().setContent(box);
        dialog.showAndWait().ifPresent(result -> { if (result == ButtonType.OK) {
            try { int rid = Integer.parseInt(resourceId.getText().trim()); int qty = Integer.parseInt(quantity.getText().trim());
                if (rid <= 0 || qty <= 0) throw new NumberFormatException();
                runMutation(() -> assignTransaction(request.getRequestId(), rid, qty));
            } catch (NumberFormatException e) { showAlert(Alert.AlertType.WARNING, "Invalid Input", "IDs and quantity must be positive numbers."); }
        }});
    }

    private void openEditRequestDialog(Aid_Request request) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Aid Request");
        dialog.setHeaderText("REQ-" + request.getRequestId());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField category = new TextField(safe(request.getCategory()));
        ComboBox<String> urgency = new ComboBox<>(FXCollections.observableArrayList("HIGH", "MEDIUM", "LOW"));
        urgency.setValue(upper(request.getUrgency()));
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("Pending", "In Progress", "Fulfilled", "Cancelled"));
        status.setValue(statusValue(request.getStatus()));
        TextArea description = new TextArea(safe(request.getDescription()));
        description.setWrapText(true); description.setPrefRowCount(4);
        VBox content = new VBox(8, new Label("Category"), category, new Label("Urgency"), urgency,
                new Label("Status"), status, new Label("Description"), description);
        content.setPrefWidth(380); dialog.getDialogPane().setContent(content);
        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            if (category.getText().isBlank() || urgency.getValue() == null || status.getValue() == null
                    || description.getText().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Input", "All request fields are required.");
                return;
            }
            runMutation(() -> updateRequest(request.getRequestId(), category.getText().trim(),
                    urgency.getValue(), status.getValue(), description.getText().trim()));
        });
    }

    private void updateRequest(int requestId, String category, String urgency, String status, String description) {
        String sql = "UPDATE AID_REQUEST SET category=?, urgency=?, status=?, description=? WHERE request_id=?";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, category.toLowerCase(Locale.ROOT));
            p.setString(2, urgency.toUpperCase(Locale.ROOT));
            p.setString(3, status);
            p.setString(4, description);
            p.setInt(5, requestId);
            if (p.executeUpdate() != 1) throw new SQLException("Request not found");
            audit(c, "EDIT", "REQUEST", requestId, "status=" + status);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void confirmDeleteRequest(Aid_Request request) {
        if (confirm("Delete Aid Request", "Delete REQ-" + request.getRequestId() + "?"))
            runMutation(() -> deleteRequest(request.getRequestId()));
    }

    private void deleteRequest(int requestId) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement deleteAssignments = c.prepareStatement("DELETE FROM ASSIGNMENT WHERE request_id=?")) {
                    deleteAssignments.setInt(1, requestId);
                    deleteAssignments.executeUpdate();
                }
                try (PreparedStatement delete = c.prepareStatement("DELETE FROM AID_REQUEST WHERE request_id=?")) {
                    delete.setInt(1, requestId);
                    if (delete.executeUpdate() != 1) throw new SQLException("Request not found");
                }
                audit(c, "DELETE", "REQUEST", requestId, "request removed");
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private String statusValue(String value) {
        String normalized = lower(value);
        if (normalized.equals(PENDING)) return "Pending";
        if (normalized.equals(IN_PROGRESS)) return "In Progress";
        if (normalized.equals(FULFILLED)) return "Fulfilled";
        return "Cancelled";
    }

    private void assignTransaction(int requestId, int resourceId, int quantity) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                String category;
                int resourceQuantity;
                try (PreparedStatement p = c.prepareStatement("SELECT category, quantity FROM RESOURCE WHERE resource_id=? FOR UPDATE")) {
                    p.setInt(1, resourceId); try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Resource not found");
                        category = rs.getString("category");
                        resourceQuantity = rs.getInt("quantity");
                    }
                }
                String requestCategory, status;
                try (PreparedStatement p = c.prepareStatement("SELECT category,status FROM AID_REQUEST WHERE request_id=? FOR UPDATE")) {
                    p.setInt(1, requestId); try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Request not found");
                        requestCategory = rs.getString("category"); status = rs.getString("status");
                    }
                }
                if (!PENDING.equals(lower(status)) || !safe(category).equalsIgnoreCase(safe(requestCategory)))
                    throw new SQLException("Request is not compatible with this resource");
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT COALESCE(SUM(assigned_quantity),0) FROM ASSIGNMENT " +
                                "WHERE resource_id=? AND LOWER(status) IN ('assigned','on the way')")) {
                    p.setInt(1, resourceId);
                    try (ResultSet rs = p.executeQuery()) {
                        int reserved = rs.next() ? rs.getInt(1) : 0;
                        if (reserved + quantity > resourceQuantity) throw new SQLException("Insufficient unreserved resource");
                    }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM ASSIGNMENT WHERE request_id=? AND LOWER(status) NOT IN ('delivered','cancelled')")) {
                    p.setInt(1, requestId); try (ResultSet rs = p.executeQuery()) { if (rs.next() && rs.getInt(1) > 0) throw new SQLException("Request already assigned"); }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO ASSIGNMENT(request_id,resource_id,assigned_quantity,status) VALUES(?,?,?,'Assigned')")) {
                    p.setInt(1, requestId); p.setInt(2, resourceId); p.setInt(3, quantity); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE AID_REQUEST SET status='In Progress' WHERE request_id=? AND LOWER(status)='pending'")) {
                    p.setInt(1, requestId); if (p.executeUpdate() != 1) throw new SQLException("Request status changed");
                }
                audit(c, "ASSIGN", "REQUEST", requestId, "resource=" + resourceId + ", quantity=" + quantity);
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void openEditResourceDialog(Resource resource) {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Edit Resource");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField quantity = new TextField(String.valueOf(resource.getQuantity()));
        TextArea description = new TextArea(safe(resource.getDescription())); description.setWrapText(true); description.setPrefRowCount(3);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8, new Label("Quantity"), quantity, new Label("Description"), description);
        box.setPrefWidth(350); dialog.getDialogPane().setContent(box);
        dialog.showAndWait().ifPresent(result -> { if (result == ButtonType.OK) try {
            int value = Integer.parseInt(quantity.getText().trim()); if (value < 0) throw new NumberFormatException();
            runMutation(() -> updateResource(resource.getResourceId(), value, description.getText().trim()));
        } catch (NumberFormatException e) { showAlert(Alert.AlertType.WARNING, "Invalid Input", "Quantity must be zero or greater."); }});
    }

    private void updateResource(int resourceId, int quantity, String description) {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(
                "UPDATE RESOURCE SET quantity=?,description=? WHERE resource_id=?")) {
            try (PreparedStatement reserved = c.prepareStatement("SELECT COALESCE(SUM(assigned_quantity),0) FROM ASSIGNMENT WHERE resource_id=? AND LOWER(status) IN ('assigned','on the way')")) {
                reserved.setInt(1, resourceId); try (ResultSet rs = reserved.executeQuery()) {
                    if (rs.next() && quantity < rs.getInt(1)) throw new SQLException("Quantity is below reserved amount");
                }
            }
            p.setInt(1, quantity); p.setString(2, description); p.setInt(3, resourceId);
            if (p.executeUpdate() != 1) throw new SQLException("Resource not found");
            audit(c, "EDIT", "RESOURCE", resourceId, "quantity=" + quantity);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void confirmDelete(Resource resource) {
        if (confirm("Delete Resource", "Delete RES-" + resource.getResourceId() + "?"))
            runMutation(() -> deleteResource(resource.getResourceId()));
    }

    private void deleteResource(int resourceId) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE AID_REQUEST req JOIN ASSIGNMENT a ON a.request_id=req.request_id " +
                                "SET req.status='Pending' WHERE a.resource_id=? " +
                                "AND LOWER(a.status) NOT IN ('delivered','cancelled') " +
                                "AND LOWER(req.status)='in progress'")) {
                    p.setInt(1, resourceId);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM ASSIGNMENT WHERE resource_id=?")) {
                    p.setInt(1, resourceId);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM RESOURCE WHERE resource_id=?")) {
                    p.setInt(1, resourceId);
                    if (p.executeUpdate() != 1) throw new SQLException("Resource not found");
                }
                audit(c, "DELETE", "RESOURCE", resourceId, "resource and linked assignments removed");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void openUpdateAssignmentDialog(Assignment assignment) {
        List<String> statuses = List.of("Assigned", "On the Way", "Delivered");
        ChoiceDialog<String> dialog = new ChoiceDialog<>(assignment.getStatus(), statuses);
        dialog.setTitle("Update Assignment"); dialog.setHeaderText("ASN-" + assignment.getAssignmentId());
        dialog.setContentText("New status:"); dialog.showAndWait().ifPresent(status -> runMutation(() -> updateAssignment(assignment.getAssignmentId(), status)));
    }

    private void updateAssignment(int assignmentId, String newStatus) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int requestId, resourceId, quantity; String oldStatus;
                try (PreparedStatement p = c.prepareStatement("SELECT request_id,resource_id,assigned_quantity,status FROM ASSIGNMENT WHERE assignment_id=? FOR UPDATE")) {
                    p.setInt(1, assignmentId); try (ResultSet rs = p.executeQuery()) { if (!rs.next()) throw new SQLException("Assignment not found");
                        requestId=rs.getInt(1); resourceId=rs.getInt(2); quantity=rs.getInt(3); oldStatus=rs.getString(4); }
                }
                validateTransition(oldStatus, newStatus);
                if (DELIVERED.equals(lower(newStatus)) && !DELIVERED.equals(lower(oldStatus))) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE RESOURCE SET quantity=quantity-? WHERE resource_id=? AND quantity>=?")) {
                        p.setInt(1, quantity); p.setInt(2, resourceId); p.setInt(3, quantity); if (p.executeUpdate()!=1) throw new SQLException("Insufficient resource quantity");
                    }
                    try (PreparedStatement p = c.prepareStatement("UPDATE AID_REQUEST SET status='Fulfilled' WHERE request_id=?")) { p.setInt(1, requestId); p.executeUpdate(); }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE ASSIGNMENT SET status=? WHERE assignment_id=?")) { p.setString(1,newStatus); p.setInt(2,assignmentId); p.executeUpdate(); }
                audit(c, "STATUS_CHANGE", "ASSIGNMENT", assignmentId, "status=" + newStatus);
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void validateTransition(String oldStatus, String newStatus) throws SQLException {
        String oldValue=lower(oldStatus), newValue=lower(newStatus);
        if (DELIVERED.equals(oldValue) || !(ASSIGNED.equals(newValue) || ON_THE_WAY.equals(newValue) || DELIVERED.equals(newValue)))
            throw new SQLException("Invalid assignment status transition");
        if (ASSIGNED.equals(oldValue) && DELIVERED.equals(newValue)) throw new SQLException("Assignment must be On the Way first");
    }

    private void confirmCancel(Assignment assignment) {
        if (confirm("Cancel Assignment", "Cancel ASN-" + assignment.getAssignmentId() + "?"))
            runMutation(() -> cancelAssignment(assignment.getAssignmentId()));
    }

    private void cancelAssignment(int assignmentId) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int requestId; String status;
                try (PreparedStatement p = c.prepareStatement("SELECT request_id,status FROM ASSIGNMENT WHERE assignment_id=? FOR UPDATE")) {
                    p.setInt(1,assignmentId); try(ResultSet rs=p.executeQuery()){ if(!rs.next()) throw new SQLException("Assignment not found"); requestId=rs.getInt(1); status=rs.getString(2); }
                }
                if (DELIVERED.equals(lower(status))) throw new SQLException("Delivered assignment cannot be cancelled");
                try (PreparedStatement p=c.prepareStatement("DELETE FROM ASSIGNMENT WHERE assignment_id=?")){p.setInt(1,assignmentId);p.executeUpdate();}
                try (PreparedStatement p=c.prepareStatement("UPDATE AID_REQUEST SET status='Pending' WHERE request_id=? AND LOWER(status)='in progress' AND NOT EXISTS (SELECT 1 FROM ASSIGNMENT WHERE request_id=? AND LOWER(status) NOT IN ('delivered','cancelled'))")){p.setInt(1,requestId);p.setInt(2,requestId);p.executeUpdate();}
                audit(c, "CANCEL", "ASSIGNMENT", assignmentId, "request=" + requestId);
                c.commit();
            } catch(SQLException e){c.rollback();throw e;} finally{c.setAutoCommit(true);}
        } catch(SQLException e){throw new RuntimeException(e);}
    }

    private void runMutation(Runnable mutation) {
        CompletableFuture.runAsync(mutation, EXECUTOR).thenRun(() -> Platform.runLater(this::loadAllData))
                .exceptionally(error -> { LOGGER.log(Level.WARNING,"Operator mutation failed",error); Platform.runLater(() -> showAlert(Alert.AlertType.ERROR,"Operation Failed", rootMessage(error))); return null; });
    }

    private void audit(Connection connection, String action, String entityType, int entityId, String details) {
        AuthService.UserSession session = AuthService.getActiveSession();
        if (session == null) return;
        String sql = "INSERT INTO OPERATOR_AUDIT_LOG(operator_user_id,action,entity_type,entity_id,details) VALUES(?,?,?,?,?)";
        try (PreparedStatement p = connection.prepareStatement(sql)) {
            p.setInt(1, session.userId); p.setString(2, action); p.setString(3, entityType);
            p.setInt(4, entityId); p.setString(5, details); p.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not write operator audit event", e);
        }
    }

    private void updateDashboard(int[] values) {
        updateCard(values[0], foodStockLabel, foodProgressBar, foodStatusBadge, foodPercentLabel, "Units");
        updateCard(values[1], medStockLabel, medProgressBar, medStatusBadge, medPercentLabel, "Units");
        updateCard(values[2], shelterStockLabel, shelterProgressBar, shelterStatusBadge, shelterPercentLabel, "Kits");
        updateCard(values[3], clothingStockLabel, clothingProgressBar, clothingStatusBadge, clothingPercentLabel, "Sets");
    }

    private void updateSummaryStats() {
        if (statActiveReqsLabel != null) statActiveReqsLabel.setText(String.valueOf(aidRequestList.size()));
        if (statPendingAsnLabel != null) {
            long pending = assignmentList.stream()
                    .filter(item -> ASSIGNED.equals(lower(item.getStatus())) || ON_THE_WAY.equals(lower(item.getStatus())))
                    .count();
            statPendingAsnLabel.setText(String.valueOf(pending));
        }
        if (statDepotsLabel != null) {
            long locations = resourceList.stream().map(Resource::getLocationId).distinct().count();
            statDepotsLabel.setText(locations + " Active");
        }
    }
    private void updateCard(int value, Label label, ProgressBar bar, Label badge, Label percent, String unit) {
        double percentage=Math.min(1.0,value/250000.0);
        label.setText(value+" "+unit);
        bar.setProgress(percentage);
        updateBadge(badge, percentage);
        percent.setText(Math.round(percentage * 100) + "%");
    }
    private void updateBadge(Label badge,double percentage){badge.setText(percentage>=.5?"STABLE":percentage>=.2?"MODERATE":"CRITICAL");}

    @FXML private void handleLogout(ActionEvent event) {
        try { AuthService.clearSession(); Parent root=FXMLLoader.load(getClass().getResource("/com/pnrl/crisasterhub/fxml/login.fxml")); Stage stage=(Stage)((Node)event.getSource()).getScene().getWindow(); stage.setScene(new Scene(root)); stage.setFullScreen(true); }
        catch(IOException e){LOGGER.log(Level.WARNING,"Could not return to login",e);showAlert(Alert.AlertType.ERROR,"System Error","Could not return to login screen.");}
    }

    private static String safe(String value){return value==null||value.isBlank()?"-":value;}
    private static String upper(String value){return safe(value).toUpperCase(Locale.ROOT);}
    private static String lower(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    private static boolean contains(Object value,String filter){return String.valueOf(value==null?"":value).toLowerCase(Locale.ROOT).contains(filter);}
    private static SimpleStringProperty text(String value){return new SimpleStringProperty(value);}
    private static String format(LocalDateTime value){return value==null?"-":value.toString();}
    private static LocalDateTime toDate(Timestamp value){return value==null?null:value.toLocalDateTime();}
    private String urgencyClass(String value){String v=lower(value);return v.contains("high")||v.contains("critical")?"badge-critical":v.contains("medium")||v.contains("urgent")?"badge-urgent":"badge-moderate";}
    private String statusClass(String value){String v=lower(value);return v.equals(PENDING)?"badge-pending":v.equals(FULFILLED)||v.equals(DELIVERED)?"badge-completed":v.equals(IN_PROGRESS)||v.equals(ON_THE_WAY)?"badge-active":"badge-open";}
    private boolean confirm(String title,String message){Alert alert=new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.OK,ButtonType.CANCEL);alert.setTitle(title);return alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    private String rootMessage(Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();return safe(cause.getMessage());}
    private void showAlert(Alert.AlertType type,String title,String message){Alert alert=new Alert(type);alert.setTitle(title);alert.setHeaderText(null);alert.setContentText(message);alert.showAndWait();}
}
