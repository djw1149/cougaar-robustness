=begin experiment

name: ACUC-2-AgentCompromiseSAVE
description: Save a society after Stage 5 for the Agent Compromise experiments
script: $CIP/csmart/scripts/definitions/UR-BaselineTemplate-ExtOplan.rb
parameters:
  - run_count: 1
  - society_file: $CIP/csmart/config/societies/ua/full-tc20-avn-162a208v.plugins.rb
  - layout_file: $CIP/operator/layouts/UR-557-layout-1.xml
  - archive_dir: $CIP/Logs
  
  - rules:
    - $CIP/csmart/config/rules/isat
    - $CIP/csmart/config/rules/yp
    - $CIP/csmart/config/rules/logistics
    - $CIP/csmart/config/rules/robustness
    - $CIP/csmart/config/rules/robustness/common
    - $CIP/csmart/config/rules/coordinator
#    - $CIP/csmart/config/rules/coordinator/examples/sample_defense
    - $CIP/csmart/config/rules/coordinator/test
    - $CIP/csmart/config/rules/robustness/uc1
#    - $CIP/csmart/config/rules/robustness/uc2
    - $CIP/csmart/config/rules/robustness/uc7
    - $CIP/csmart/config/rules/metrics/basic
    - $CIP/csmart/config/rules/metrics/sensors
    - $CIP/csmart/config/rules/metrics/serialization/metrics-only-serialization.rule
    - $CIP/csmart/config/rules/metrics/rss/tic
#    - $CIP/csmart/config/rules/robustness/debug_rules/messageTrace.rule
  - community_rules:
    - $CIP/csmart/config/rules/robustness/communities

include_scripts:
  - script: $CIP/csmart/lib/isat/clearPnLogs.rb
  - script: $CIP/csmart/lib/isat/initialize_network.rb
  - script: $CIP/csmart/lib/isat/stop_society.rb
    parameters:
      - stop_location: after_stage_5

  - script: $CIP/csmart/lib/isat/save_snapshot.rb
    parameters:
      - snapshot_name: $CIP/SAVE-PreStage5-ARUC7.tgz
      - snapshot_location: before_stage_5
#  - script: $CIP/csmart/lib/coordinator/unleash_defenses.rb 
#    parameters:
#      - location: during_stage_1
#      - verbose: 1
=end

require 'cougaar/scripting'
Cougaar::ExperimentDefinition.register(__FILE__)