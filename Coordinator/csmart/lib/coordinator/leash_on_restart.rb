=begin script

include_path: leash_on_restart.rb
description: Leash All Defenses on a Full Society Restore and Unleash them at starting_stage

=end

CIP = ENV['CIP']

$:.unshift File.join(CIP, 'csmart', 'config', 'lib')
require 'coordinator/leashing'

insert_after :snapshot_restored do
  do_action "LeashOnRestart"
end

insert_after :starting_stage do
  do_action "MonitorUnleash", 1
  do_action "Unleash", 1
  wait_for "Unleashed", 1
end