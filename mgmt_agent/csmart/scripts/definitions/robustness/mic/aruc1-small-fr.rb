=begin experiment

name: ARUC1-No-Stress
description: AR UC1 - No Stresses
script: $CIP/csmart/scripts/definitions/BaselineTemplate-ExtOplan.rb

parameters:
  - run_count: 1
  - society_file: $CIP/csmart/config/societies/ad/SMALL-1AD-TC20.rb
  - layout_file: $CIP/operator/uc1-small-1ad-layout.xml
  - archive_dir: $CIP/Logs

  - rules:
    - $CIP/csmart/config/rules/isat
    - $CIP/csmart/config/rules/yp
    - $CIP/csmart/config/rules/logistics
    - $CIP/csmart/config/rules/robustness/manager.rule
    - $CIP/csmart/config/rules/coordinator
    - $CIP/csmart/config/rules/robustness/uc1
    - $CIP/csmart/config/rules/robustness/uc1/debug
    - $CIP/csmart/config/rules/robustness/uc4
    - $CIP/csmart/config/rules/robustness/uc9

  - community_rules:
    - $CIP/csmart/config/rules/robustness/communities

include_scripts:
  - script: $CIP/csmart/lib/isat/clearPnLogs.rb
  - script: $CIP/csmart/lib/robustness/mic/forced_restart.rb
  - script: $CIP/csmart/lib/coordinator/unleash_defenses.rb

  - script: $CIP/csmart/lib/isat/stop_society.rb
    parameters:
      - stop_location: before_stage_3

=end

require 'cougaar/scripting'
Cougaar::ExperimentDefinition.register(__FILE__)
