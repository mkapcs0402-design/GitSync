extern crate serde;
extern crate serde_yaml;

use serde::{Deserialize, Serialize};
use serde_yaml::Value;
use std::env as env_internal;
use std::fs::File;
use std::io::Write;
use std::process::{Command, Stdio};
mod env;

#[path = "../onboarding/src/mod.rs"]
mod onboarding;

#[path = "../auth/src/mod.rs"]
mod auth;

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
        let before_each_steps = if input.before_each.len() < 1 {
            &vec![]
        } else {
            if index < input.before_each.len() {
                &input.before_each[index]
            } else {
                &input.before_each[input.before_each.len() - 1]
            }
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

    let mut file = File::create(format!("{}.yaml", output_file))?;
    for item in yaml_data {
        writeln!(file, "{}", item.as_str().unwrap())?;
    }

    println!(
        "YAML file generated successfully! {}",
        format!("{}.yaml", output_file)
    );

    Ok(())
}

fn validate_url_format(key: &str, url: &str) -> bool {
    if key.ends_with("_PROTOCOL_URL") {
        let protocol_url_pattern =
            regex::Regex::new(r"^ssh://[^@]+@[\w.-]+(?:\.[\w\.-]+)+[/\w\.-]+\.git$").unwrap();
        protocol_url_pattern.is_match(url)
    } else if key.ends_with("_AT_URL") {
        let at_url_pattern = regex::Regex::new(r"^git@[\w.-]+:[\w\.-]+/[\w\.-]+\.git$").unwrap();
        at_url_pattern.is_match(url)
    } else if key.ends_with("_URL") {
        let url_pattern = regex::Regex::new(r"^https://[\w.-]+(?:\.[\w\.-]+)+[/\w\.-]*$").unwrap();
        url_pattern.is_match(url)
    } else {
        false
    }
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = env_internal::args().collect();
    let env_vars = env::setup();

    println!();
    for (key, value) in &env_vars {
        println!("{}={}", key, value);
    }
    println!();

    for (key, value) in &env_vars {
        if key.ends_with("_URL") {
            if !validate_url_format(key, value) {
                return Err(format!("Invalid URL format for {}: {}", key, value).into());
            }
        }
    }

    for (key, value) in &env_vars {
        env_internal::set_var(key, value);
    }

    create_yaml(&onboarding::negative::get_input(), "onboarding/negative")?;
    create_yaml(&onboarding::positive::get_input(), "onboarding/positive")?;

    create_yaml(&auth::github::get_input(), "auth/github")?;
    create_yaml(&auth::gitea::get_input(), "auth/gitea")?;
    create_yaml(&auth::https::get_input(), "auth/https")?;
    create_yaml(&auth::ssh::get_input(), "auth/ssh")?;

    let path = if args.len() > 1 {
        &args[1]
    } else {
        &".".to_string()
    };

    let output = Command::new("maestro")
        .arg("test")
        .arg(path)
        .stdout(Stdio::inherit())
        .output()
        .expect("Failed to execute command");

    if output.status.success() {
        println!("Command executed successfully!",);
    } else {
        eprintln!(
            "Command failed:\n{}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    Ok(())
}
