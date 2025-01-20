use crate::Input;

pub fn get_input() -> Input {
    Input {
        before_all: vec!["disable_all_files_access", "clear_state"],
        before_each: vec![vec!["launch_stls_no_perms"], vec!["launch_notif_perm"]],
        flows: vec![
            vec![
                vec![
                    vec!["welcome_dialog/negative", "notifications_dialog/assert"],
                    vec!["welcome_dialog/positive", "notifications_dialog/assert"],
                ],
                vec![vec![
                    "notifications_dialog/positive",
                    "notifications_permission/assert",
                    "back",
                    "notifications_dialog/assert",
                ]],
                vec![vec![
                    "notifications_dialog/positive_secondary",
                    "notifications_permission/enable",
                    "back",
                    "notifications_dialog/positive_secondary",
                ]],
            ],
            vec![
                vec![vec![
                    "welcome_dialog/positive",
                    "all_files_dialog/positive",
                    "all_files_permission/assert",
                    "back",
                    "all_files_dialog/assert",
                ]],
                vec![vec![
                    "all_files_dialog/positive_secondary",
                    "all_files_permission/enable",
                    "back",
                    "almost_there_dialog/assert",
                ]],
            ],
            vec![vec![
                vec![
                    "almost_there_dialog/negative",
                    "almost_there_dialog/assert_not",
                ],
                vec!["almost_there_dialog/positive", "pre_auth_dialog/assert"],
            ]],
            vec![
                vec![
                    vec!["pre_auth_dialog/negative", "pre_auth_dialog/assert_not"],
                    vec!["pre_auth_dialog/positive", "../auth/flows/assert"],
                ],
                vec![vec![
                    "../auth/flows/github/start",
                    "../auth/flows/github/assert",
                ]],
                vec![
                    vec!["back", "pre_auth_dialog/assert"],
                    vec!["../auth/flows/github/auth", "../clone/flows/assert"],
                ],
            ],
            vec![
                vec![vec!["../clone/flows/list/github"]],
                vec![
                    vec![
                        "../clone/flows/select_folder_dialog/negative",
                        "../clone/flows/select_folder_dialog/assert_not",
                    ],
                    vec![
                        "../clone/flows/select_folder_dialog/positive",
                        "../clone/flows/select_folder/assert",
                    ],
                ],
                vec![
                    vec![
                        "../clone/flows/select_folder/negative",
                        "../clone/flows/select_folder/assert_not",
                        "../clone/flows/select_folder_dialog/assert_not",
                        "../clone/flows/url/assert",
                    ],
                    vec![
                        "../clone/flows/select_folder/positive",
                        "../clone/flows/select_folder/assert_not",
                        "../clone/flows/select_folder_dialog/assert_not",
                        "auto_sync_dialog/assert",
                    ],
                ],
            ],
            vec![vec![
                vec!["auto_sync_dialog/negative", "auto_sync_dialog/assert_not"],
                vec![
                    "../home/flows/sync_now/assert",
                    "auto_sync_dialog/assert_not",
                ],
            ]],
        ],
    }
}
