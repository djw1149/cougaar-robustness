CIP = ENV['CIP']
RULES = File.join(CIP, 'csmart','config','rules')

$:.unshift File.join(CIP, 'csmart', 'acme_scripting', 'src', 'lib')
$:.unshift File.join(CIP, 'csmart', 'acme_service', 'src', 'redist')
$:.unshift File.join(CIP, 'csmart', 'config', 'lib')

require 'cougaar/scripting'
require 'ultralog/scripting'
require 'robustness/uc1/aruc1_actions_and_states'
require 'robustness/uc9/deconfliction'

HOSTS_FILE = Ultralog::OperatorUtils::HostManager.new.get_hosts_file

Cougaar::ExperimentMonitor.enable_stdout
Cougaar::ExperimentMonitor.enable_logging

Cougaar.new_experiment("ARUC1_Auto_Load_Balance").run(1) {

  do_action "LoadSocietyFromScript", "#{CIP}/csmart/config/societies/ad/FULL-1AD-TRANS-1359.rb"
  do_action "LayoutSociety", "#{CIP}/operator/1ad-layout.xml", HOSTS_FILE

  do_action "TransformSociety", false,
    "#{RULES}/isat",
    "#{RULES}/logistics",
    "#{RULES}/robustness/manager.rule",
    "#{RULES}/robustness/uc1/manager.rule",
    "#{RULES}/robustness/uc1/aruc1.rule",
    "#{RULES}/robustness/uc1/mic.rule",
    "#{RULES}/robustness/uc9/deconfliction.rule",
    "#{RULES}/metrics/basic",
    "#{RULES}/metrics/sensors"

  do_action "TransformSociety", false, "#{RULES}/robustness/communities"

  #do_action "SaveCurrentSociety", "mySociety.xml"
  #do_action "SaveCurrentCommunities", "myCommunities.xml"

  do_action "StartJabberCommunications"
  do_action "CleanupSociety"
  do_action "VerifyHosts"

  do_action "DeployCommunitiesFile"

  #do_action "DisableDeconfliction"

  do_action "ConnectOperatorService"
  do_action "ClearPersistenceAndLogs"
  do_action "InstallCompletionMonitor"

  do_action "StartSociety"

  ## Print events from Robustness Controller
  do_action "GenericAction" do |run|
    run.comms.on_cougaar_event do |event|
      if event.component.include?("RobustnessController")
        puts event
      end
    end
  end

  # After CommunityReady event is received wait for persistence
  wait_for "CommunitiesReady", ["1AD-FWD-COMM", "1AD-REAR-COMM"]

  # Add an empty node to each community
  do_action "SaveHostOfNode", "1AD-FWD-COMM", "FWD-A"
  do_action "AddNode", "FWD-NEW-1", "1AD-FWD-COMM"
  do_action "SaveHostOfNode", "1AD-REAR-COMM", "REAR-A"
  do_action "AddNode", "REAR-NEW-1", "1AD-REAR-COMM"

  wait_for "CommunitiesReady", ["1AD-FWD-COMM", "1AD-REAR-COMM"]

  wait_for  "GLSConnection", true
  wait_for  "NextOPlanStage"
  do_action "Sleep", 30.seconds
  do_action "PublishNextStage"

  do_action "UnleashDefenses"

  wait_for  "SocietyQuiesced"  do
    wait_for  "Command", "shutdown"
    do_action "SaveSocietyCompletion", "completion_#{experiment.name}.xml"
    #include "inventory.inc", "RunSoc"
    do_action "StopSociety"
    do_action "ArchiveLogs"
    do_action "StopCommunications"
  end

  wait_for "Command", "shutdown"
  do_action "Sleep", 30.seconds
  do_action "SaveSocietyCompletion", "completion_#{experiment.name}.xml"
  #include "inventory.inc", "RunSoc"
  do_action "Sleep", 30.seconds
  do_action "StopSociety"
  do_action "ArchiveLogs"
  do_action "StopCommunications"
}
