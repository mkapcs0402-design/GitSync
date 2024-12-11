use crate::Input;

pub fn get_input() -> Input {
    Input {
        before_all: vec!["disable_all_files_access", "clear_state"],
        before_each: vec![],
        flows: vec![vec![vec![vec![
            "launch_stls_no_perms",
            "welcome_dialog/positive",
            "notifications_dialog/positive",
            "notifications_permission/enable",
            "back",
            "notifications_dialog/positive_secondary",
            "all_files_dialog/positive",
            "all_files_permission/enable",
            "back",
            "almost_there_dialog/positive",
            "pre_auth_dialog/positive",
            "../auth/flows/github/start",
            "../auth/flows/github/assert",
            "../auth/flows/github/auth",
            "../clone/flows/list/github",
            "../clone/flows/select_folder_dialog/positive",
            "../clone/flows/select_folder/positive",
            "../clone/flows/select_folder/assert_not",
            "../clone/flows/select_folder_dialog/assert_not",
            "auto_sync_dialog/negative",
            "auto_sync_dialog/assert_not",
        ]]]],
    }
}
