package org.example.danbainoso.client.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.danbainoso.client.ClientMain;
import org.example.danbainoso.client.ClientRMI;
import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.RemoteException;
import java.util.function.Consumer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

public class GroupSettingsController {
    private static final Logger logger = LoggerUtil.getLogger(GroupSettingsController.class);

    @FXML
    private TextField groupNameField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label createdByLabel;

    @FXML
    private Label memberCountLabel;

    @FXML
    private Label infoLabel;

    @FXML
    private Button saveButton;

    @FXML
    private ListView<User> membersList;

    @FXML
    private Button addMemberButton;

    @FXML
    private Button deleteGroupButton;

    @FXML
    private Button leaveGroupButton;

    @FXML
    private Button leaveInfoButton;

    private ClientRMI clientRMI;
    private User currentUser;
    private Group currentGroup;
    private boolean isAdmin;
    private Stage stage;
    private Consumer<Group> onGroupUpdated;
    private Runnable onGroupDeleted;
    private final ObservableList<User> memberItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        clientRMI = ClientMain.getClientRMI();
        infoLabel.setText("");
        membersList.setItems(memberItems);
        membersList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setContextMenu(null);
                } else {
                    setText(formatUserDisplay(user));
                    setContextMenu(buildMemberContextMenu(user));
                }
            }
        });
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setData(User currentUser,
                        Group group,
                        boolean isAdmin,
                        Consumer<Group> onGroupUpdated,
                        Runnable onGroupDeleted) {
        this.currentUser = currentUser;
        this.currentGroup = group;
        this.isAdmin = isAdmin;
        this.onGroupUpdated = onGroupUpdated;
        this.onGroupDeleted = onGroupDeleted;
        updateEditableState();
        populateFromGroup(group);
        loadLatestGroupInfo();
        loadMembers();
    }

    private void populateFromGroup(Group group) {
        if (group == null) {
            return;
        }
        groupNameField.setText(group.getGroupName());
        descriptionArea.setText(group.getDescription() != null ? group.getDescription() : "");
        createdByLabel.setText(group.getCreatorName() != null ? group.getCreatorName() : ("ID: " + group.getCreatedBy()));
        memberCountLabel.setText(String.valueOf(group.getMemberCount()));
    }

    private void updateEditableState() {
        boolean editable = isAdmin;
        groupNameField.setEditable(editable);
        descriptionArea.setEditable(editable);
        saveButton.setDisable(!editable);
        addMemberButton.setDisable(!editable);
        addMemberButton.setVisible(editable);
        addMemberButton.setManaged(editable);
        deleteGroupButton.setVisible(false);
        deleteGroupButton.setManaged(false);
        deleteGroupButton.setDisable(true);
        leaveGroupButton.setVisible(false);
        leaveGroupButton.setManaged(false);
        leaveGroupButton.setDisable(true);
        leaveInfoButton.setVisible(false);
        leaveInfoButton.setManaged(false);

        if (editable) {
            infoLabel.setText("Bạn là admin của nhóm này. Có thể chỉnh sửa tên và mô tả.");
        } else {
            infoLabel.setText("Chỉ admin mới có thể chỉnh sửa thông tin nhóm.");
        }
    }

    private void loadLatestGroupInfo() {
        if (currentGroup == null) {
            return;
        }
        new Thread(() -> {
            try {
                Group refreshed = clientRMI.getGroupById(currentGroup.getGroupId());
                Platform.runLater(() -> {
                    if (refreshed != null) {
                        currentGroup = refreshed;
                        populateFromGroup(refreshed);
                        loadMembers();
                    }
                });
            } catch (RemoteException e) {
                logger.error("Failed to load group details", e);
                Platform.runLater(() -> showAlert("Không thể tải thông tin nhóm."));
            }
        }).start();
    }

    private void loadMembers() {
        if (currentGroup == null) {
            memberItems.clear();
            memberCountLabel.setText("0");
            refreshDeleteButtonState();
            refreshLeaveButtonState();
            return;
        }
        new Thread(() -> {
            try {
                List<User> members = clientRMI.getGroupMembers(currentGroup.getGroupId());
                Platform.runLater(() -> {
                    memberItems.setAll(members);
                    memberCountLabel.setText(String.valueOf(members.size()));
                    membersList.refresh();
                    refreshLeaveButtonState();
                    refreshDeleteButtonState();
                });
            } catch (RemoteException e) {
                logger.error("Failed to load members", e);
                Platform.runLater(() -> showAlert("Không thể tải danh sách thành viên."));
            }
        }).start();
    }

    @FXML
    private void handleSave() {
        if (!isAdmin || currentGroup == null) {
            showAlert("Bạn không có quyền chỉnh sửa nhóm này.");
            return;
        }
        String name = groupNameField.getText() != null ? groupNameField.getText().trim() : "";
        if (name.isEmpty()) {
            showAlert("Tên nhóm không được để trống.");
            return;
        }
        String description = descriptionArea.getText() != null ? descriptionArea.getText().trim() : "";

        Group updatePayload = new Group();
        updatePayload.setGroupId(currentGroup.getGroupId());
        updatePayload.setGroupName(name);
        updatePayload.setDescription(description);
        updatePayload.setAvatarUrl(currentGroup.getAvatarUrl());

        saveButton.setDisable(true);
        new Thread(() -> {
            try {
                boolean success = clientRMI.updateGroupDetails(updatePayload);
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    if (success) {
                        currentGroup.setGroupName(name);
                        currentGroup.setDescription(description);
                        showAlert("Đã cập nhật thông tin nhóm.");
                        if (onGroupUpdated != null) {
                            onGroupUpdated.accept(currentGroup);
                        }
                    } else {
                        showAlert("Không thể cập nhật thông tin nhóm. Kiểm tra quyền của bạn.");
                    }
                });
            } catch (RemoteException e) {
                logger.error("Failed to update group", e);
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    showAlert("Lỗi khi cập nhật thông tin nhóm.");
                });
            }
        }).start();
    }

    @FXML
    private void handleClose() {
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    private void handleAddMember() {
        if (!isAdmin || currentGroup == null) {
            showAlert("Chỉ admin mới có thể thêm thành viên.");
            return;
        }
        addMemberButton.setDisable(true);
        new Thread(() -> {
            try {
                List<User> friends = clientRMI.getFriends();
                List<User> members = clientRMI.getGroupMembers(currentGroup.getGroupId());
                Set<Integer> memberIds = members.stream()
                        .map(User::getUserId)
                        .collect(Collectors.toSet());
                List<User> candidates = friends.stream()
                        .filter(friend -> !memberIds.contains(friend.getUserId()))
                        .collect(Collectors.toList());
                Platform.runLater(() -> {
                    addMemberButton.setDisable(false);
                    showAddMemberDialog(candidates);
                });
            } catch (RemoteException e) {
                logger.error("Failed to prepare add-member list", e);
                Platform.runLater(() -> {
                    addMemberButton.setDisable(false);
                    showAlert("Không thể tải danh sách bạn bè.");
                });
            }
        }).start();
    }

    private void showAddMemberDialog(List<User> candidates) {
        if (candidates.isEmpty()) {
            showAlert("Không có bạn bè nào có thể thêm vào nhóm.");
            return;
        }
        LinkedHashMap<String, User> optionMap = new LinkedHashMap<>();
        for (User candidate : candidates) {
            String label = formatUserDisplay(candidate) + " (ID: " + candidate.getUserId() + ")";
            optionMap.put(label, candidate);
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(optionMap.keySet().iterator().next(),
                FXCollections.observableArrayList(optionMap.keySet()));
        dialog.setTitle("Thêm thành viên");
        dialog.setHeaderText("Chọn một người bạn để thêm vào nhóm");
        dialog.setContentText("Thành viên:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(choice -> {
            User selected = optionMap.get(choice);
            if (selected != null) {
                performAddMember(selected);
            }
        });
    }

    private void performAddMember(User user) {
        if (currentGroup == null) {
            return;
        }
        new Thread(() -> {
            try {
                boolean added = clientRMI.addMemberToGroup(currentGroup.getGroupId(), user.getUserId());
                Platform.runLater(() -> {
                    if (added) {
                        showAlert("Đã thêm " + user.getUsername() + " vào nhóm.");
                        loadMembers();
                    } else {
                        showAlert("Không thể thêm thành viên. Kiểm tra quyền hoặc trạng thái.");
                    }
                });
            } catch (RemoteException e) {
                logger.error("Failed to add member", e);
                Platform.runLater(() -> showAlert("Lỗi khi thêm thành viên."));
            }
        }).start();
    }

    private ContextMenu buildMemberContextMenu(User user) {
        if (!isAdmin || user == null || currentUser == null || user.getUserId() == currentUser.getUserId()) {
            return null;
        }
        MenuItem removeItem = new MenuItem("Xóa khỏi nhóm");
        removeItem.setOnAction(e -> confirmRemoveMember(user));
        return new ContextMenu(removeItem);
    }

    private void confirmRemoveMember(User member) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xóa thành viên");
        confirm.setHeaderText("Xóa " + member.getUsername() + " khỏi nhóm?");
        confirm.setContentText("Hành động này sẽ loại bỏ người dùng khỏi nhóm.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            performRemoveMember(member);
        }
    }

    private void performRemoveMember(User member) {
        if (currentGroup == null) {
            return;
        }
        new Thread(() -> {
            try {
                boolean removed = clientRMI.removeMemberFromGroup(currentGroup.getGroupId(), member.getUserId());
                Platform.runLater(() -> {
                    if (removed) {
                        showAlert("Đã xóa " + member.getUsername() + " khỏi nhóm.");
                        loadMembers();
                    } else {
                        showAlert("Không thể xóa thành viên. Kiểm tra quyền của bạn.");
                    }
                });
            } catch (RemoteException e) {
                logger.error("Failed to remove member", e);
                Platform.runLater(() -> showAlert("Lỗi khi xóa thành viên."));
            }
        }).start();
    }

    private String formatUserDisplay(User user) {
        if (user == null) {
            return "";
        }
        String status = user.getStatus() != null ? user.getStatus().name() : "UNKNOWN";
        return user.getUsername() + " (" + status + ")";
    }

    @FXML
    private void handleDeleteGroup() {
        if (!isAdmin || currentGroup == null) {
            showAlert("Bạn không có quyền xóa nhóm.");
            return;
        }
        if (memberItems.size() > 1) {
            showAlert("Chỉ có thể xóa nhóm khi chỉ còn 1 thành viên.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xóa nhóm");
        confirm.setHeaderText("Bạn có chắc muốn xóa nhóm này?");
        confirm.setContentText("Nhóm sẽ bị xóa vĩnh viễn.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        deleteGroupButton.setDisable(true);
        new Thread(() -> {
            try {
                boolean deleted = clientRMI.deleteGroup(currentGroup.getGroupId());
                Platform.runLater(() -> {
                    deleteGroupButton.setDisable(false);
                    if (deleted) {
                        showAlert("Đã xóa nhóm.");
                        if (onGroupDeleted != null) {
                            onGroupDeleted.run();
                        }
                        handleClose();
                    } else {
                        showAlert("Không thể xóa nhóm. Hãy chắc chắn rằng chỉ còn 1 thành viên.");
                    }
                });
            } catch (RemoteException e) {
                logger.error("Failed to delete group", e);
                Platform.runLater(() -> {
                    deleteGroupButton.setDisable(false);
                    showAlert("Lỗi khi xóa nhóm.");
                });
            }
        }).start();
    }

    @FXML
    private void handleLeaveGroup() {
        if (isAdmin) {
            showAlert("Admin cần chuyển quyền hoặc xóa nhóm trước khi rời.");
            return;
        }
        if (currentGroup == null || currentUser == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Rời nhóm");
        confirm.setHeaderText("Bạn chắc chắn muốn rời nhóm này?");
        confirm.setContentText("Bạn sẽ không nhận được tin nhắn của nhóm nữa.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        leaveGroupButton.setDisable(true);
        new Thread(() -> {
            try {
                boolean left = clientRMI.removeMemberFromGroup(currentGroup.getGroupId(), currentUser.getUserId());
                Platform.runLater(() -> {
                    leaveGroupButton.setDisable(false);
                    if (left) {
                        showAlert("Bạn đã rời nhóm.");
                        if (onGroupDeleted != null) {
                            onGroupDeleted.run();
                        }
                        handleClose();
                    } else {
                        showAlert("Không thể rời nhóm lúc này. Hãy thử lại.");
                    }
                });
            } catch (RemoteException e) {
                logger.error("Failed to leave group", e);
                Platform.runLater(() -> {
                    leaveGroupButton.setDisable(false);
                    showAlert("Lỗi khi rời nhóm.");
                });
            }
        }).start();
    }

    private void refreshLeaveButtonState() {
        if (currentUser == null) {
            leaveGroupButton.setVisible(false);
            leaveGroupButton.setManaged(false);
            leaveInfoButton.setVisible(false);
            leaveInfoButton.setManaged(false);
            return;
        }
        boolean isMember = memberItems.stream()
                .anyMatch(user -> user.getUserId() == currentUser.getUserId());
        boolean canLeave = isMember && !isAdmin;

        leaveGroupButton.setVisible(isMember);
        leaveGroupButton.setManaged(isMember);
        leaveGroupButton.setDisable(!canLeave);

        boolean needsInfo = isMember && !canLeave;
        leaveInfoButton.setVisible(needsInfo);
        leaveInfoButton.setManaged(needsInfo);
    }

    private void refreshDeleteButtonState() {
        boolean canDelete = isAdmin && memberItems.size() <= 1;
        deleteGroupButton.setVisible(canDelete);
        deleteGroupButton.setManaged(canDelete);
        deleteGroupButton.setDisable(!canDelete);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleLeaveInfo() {
        String message;
        boolean isMember = memberItems.stream()
                .anyMatch(user -> user.getUserId() == (currentUser != null ? currentUser.getUserId() : -1));
        if (isAdmin) {
            message = "Bạn là admin của nhóm. Hãy chuyển quyền admin hoặc xóa nhóm trước khi rời.";
        } else if (!isMember) {
            message = "Bạn hiện không còn trong nhóm này nên không thể thực hiện hành động.";
        } else {
            message = "Bạn phải là thành viên bình thường mới có thể rời nhóm.";
        }
        showAlert(message);
    }
}

