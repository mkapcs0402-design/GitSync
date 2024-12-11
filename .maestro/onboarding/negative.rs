extern crate serde;
extern crate serde_yaml;

use serde::{Deserialize, Serialize};
use serde_yaml::Value;
use std::fs::File;
use std::io::Write;

#[derive(Serialize, Deserialize, Debug)]
#[serde(bound(deserialize = "'de: 'static"))]
struct Input {
    before_all: Vec<&'static str>,
    before_each: Vec<Vec<&'static str>>,
    flows: Vec<Vec<Vec<Vec<&'static str>>>>,
}

fn push_flow(yaml_data: &mut Vec<Value>, flow: &str) {
    let formatted_flow = match flow {
        "disable_all_files_access" => "../common/disable_all_files_access.yaml".to_string(),
        "clear_state" => "../common/clear_state.yaml".to_string(),
        "kill" => "../common/kill.yaml".to_string(),
        "back" => "../common/back.yaml".to_string(),
        "launch_stls_no_perms" => "../common/launch_stls_no_perms.yaml".to_string(),
        "launch_notif_perm" => "../common/launch_notif_perm.yaml".to_string(),
        _ => {
            if flow.starts_with("../") {
                format!("{}.yaml", flow)
            } else {
                format!("flows/{}.yaml", flow)
            }
        }
    };

    yaml_data.push(Value::String(format!("- runFlow: {}", formatted_flow)));
}

fn create_yaml(input: &Input, output_file: &str) -> Result<(), Box<dyn std::error::Error>> {
    let mut yaml_data: Vec<Value> = Vec::new();

    yaml_data.push(Value::String("appId: com.viscouspot.gitsync".to_string()));
    yaml_data.push(Value::String("---".to_string()));
    yaml_data.push(Value::String("".to_string()));

    for item in &input.before_all {
        push_flow(&mut yaml_data, item);
    }

    push_flow(&mut yaml_data, "kill");
    yaml_data.push(Value::String("".to_string()));

    let mut last_flows: Vec<&str> = Vec::new();

    for (index, flow_group) in input.flows.iter().enumerate() {
        last_flows.clear();
        let before_each_steps = if index < input.before_each.len() {
            &input.before_each[index]
        } else {
            &input.before_each[input.before_each.len() - 1]
        };

        for flow_steps in flow_group {
            for (i, flows) in flow_steps.iter().enumerate() {
                for before_each in before_each_steps {
                    push_flow(&mut yaml_data, before_each);
                }

                for flow in &last_flows {
                    push_flow(&mut yaml_data, flow);
                }

                for flow in flows {
                    push_flow(&mut yaml_data, flow);
                }

                if i == flow_steps.len() - 1 {
                    for flow in flows {
                        last_flows.push(flow);
                    }
                }

                push_flow(&mut yaml_data, "kill");
                yaml_data.push(Value::String("".to_string()));
            }
        }

        yaml_data.push(Value::String("".to_string()));
    }

    let mut file = File::create(output_file)?;
    for item in yaml_data {
        writeln!(file, "{}", item.as_str().unwrap())?;
    }

    Ok(())
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let input = Input {
        before_all: vec!["disable_all_files_access", "clear_state"],
        before_each: vec![vec!["launch_stls_no_perms"], vec!["launch_notif_perm"]],
        flows: vec![
            vec![
                vec![vec!["welcome_dialog/assert"]],
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
                    "notifications_dialog/positive",
                    "notifications_permission/assert",
                    "notifications_permission/enable",
                    "back",
                ]],
            ],
            vec![
                vec![vec![
                    "welcome_dialog/assert",
                    "welcome_dialog/positive",
                    "all_files_dialog/assert",
                    "all_files_dialog/positive",
                    "all_files_permission/assert",
                    "back",
                    "all_files_dialog/assert",
                ]],
                vec![vec![
                    "all_files_dialog/positive",
                    "all_files_permission/assert",
                    "all_files_permission/enable",
                    "back",
                    "all_files_dialog/assert",
                    "all_files_dialog/positive_secondary",
                    "almost_there_dialog/assert",
                ]],
            ],
            vec![
                vec![vec!["almost_there_dialog/assert"]],
                vec![
                    vec![
                        "almost_there_dialog/negative",
                        "almost_there_dialog/assert_not",
                    ],
                    vec!["almost_there_dialog/positive", "pre_auth_dialog/assert"],
                ],
            ],
            vec![
                vec![vec!["pre_auth_dialog/assert"]],
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
                vec![vec!["../clone/flows/list/assert"]],
                vec![vec!["../clone/flows/list/positive"]],
            ],
        ],
    };

    create_yaml(&input, "negative.yaml")?;
    println!("YAML file generated successfully!");

    Ok(())
}
