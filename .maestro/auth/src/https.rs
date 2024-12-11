use crate::Input;

pub fn get_input() -> Input {
    Input {
        before_all: vec!["../onboarding/positive"],
        before_each: vec![],
        flows: vec![vec![vec![vec![
            "launch_notif_perm",
            "open_auth/positive",
            "https/start",
            "https/auth",
            "../clone/flows/assert",
            "../clone/flows/list/assert_not",
        ]]]],
    }
}
