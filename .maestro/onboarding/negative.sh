#!/bin/bash;

use this yaml to create a rust function that can take the following args and run the equivalent of the yaml above by running each yaml file with the bash command "maestro test {yaml_path}"

beforeEach should be the same size as flows and the corresponding flows from beforeEach should run before each index in the flows array


- runFlow: ../common/disable_all_files_access.yaml
- runFlow: ../common/clear_state.yaml
- runFlow: ../common/kill.yaml

- runFlow: ../common/launch_stls_no_perms.yaml
- runFlow: flows/welcome_dialog/assert.yaml
- runFlow: ../common/kill.yaml

- runFlow: ../common/launch_stls_no_perms.yaml
- runFlow: flows/welcome_dialog/assert.yaml
- runFlow: flows/welcome_dialog/negative.yaml
- runFlow: flows/notifications_dialog/assert.yaml
- runFlow: ../common/kill.yaml

- runFlow: ../common/launch_stls_no_perms.yaml
- runFlow: flows/welcome_dialog/assert.yaml
- runFlow: flows/welcome_dialog/positive.yaml
- runFlow: flows/notifications_dialog/assert.yaml
- runFlow: ../common/kill.yaml

- runFlow: ../common/launch_stls_no_perms.yaml
- runFlow: flows/welcome_dialog/assert.yaml
- runFlow: flows/welcome_dialog/positive.yaml
- runFlow: flows/notifications_dialog/assert.yaml
- runFlow: flows/notifications_dialog/positive.yaml
- runFlow: flows/notifications_permission/assert.yaml
- runFlow: ../common/back.yaml
- runFlow: flows/notifications_dialog/assert.yaml
- runFlow: ../common/kill.yaml

- runFlow: ../common/launch_stls_no_perms.yaml
- runFlow: flows/welcome_dialog/assert.yaml
- runFlow: flows/welcome_dialog/positive.yaml
- runFlow: flows/notifications_dialog/assert.yaml
- runFlow: flows/notifications_dialog/positive.yaml
- runFlow: flows/notifications_permission/assert.yaml
- runFlow: ../common/back.yaml
- runFlow: flows/notifications_dialog/assert.yaml
- runFlow: flows/notifications_dialog/positive.yaml
- runFlow: flows/notifications_permission/assert.yaml
- runFlow: flows/notifications_permission/enable.yaml
- runFlow: ../common/back.yaml
- assertVisible: 'Enable "All Files Access"'
- runFlow: ../common/kill.yaml




{
    beforeAll: [
        "../common/disable_all_files_access.yaml",
        "../common/clear_state.yaml",
    ],
    beforeEach: [
        [
            "../common/launch_stls_no_perms.yaml"
        ],
        [
            "../common/launch_notif_perm.yaml"
        ]
    ],
    flows: [
        [
            [
                "flows/welcome_dialog/assert.yaml",
            ],
            [
                [
                    "flows/welcome_dialog/negative.yaml",
                    "flows/notifications_dialog/assert.yaml"
                ],
                [
                    "flows/welcome_dialog/positive.yaml",
                    "flows/notifications_dialog/assert.yaml"
                ]
            ],
            [
                "flows/notifications_dialog/positive.yaml",
                "flows/notifications_permission/assert.yaml",
                "../common/back.yaml",
                "flows/notifications_dialog/assert.yaml"
            ],
            [
                "flows/notifications_dialog/positive.yaml",
                "flows/notifications_permission/assert.yaml",
                "flows/notifications_permission/enable.yaml",
                "../common/back.yaml"
            ]
        ],
        [
            [
                "flows/welcome_dialog/assert.yaml",
            ],
        ]
    ]
}