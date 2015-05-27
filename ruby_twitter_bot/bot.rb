#!/usr/bin/env ruby

require 'rubygems'
require 'bundler/setup'
require 'yaml/store'
require 'twitter'
require 'bunny'
require 'retryable'
require 'edn'

store = YAML::Store.new('bot.yaml')

client = Twitter::REST::Client.new do |config|
  store.transaction do
    config.consumer_key = store[:consumer_key]
    config.consumer_secret = store[:consumer_secret]
    config.access_token = store[:access_token]
    config.access_token_secret = store[:access_token_secret]
  end
end

connection = Bunny.new
connection.start
input_channel = connection.create_channel
input_queue = input_channel.queue('speech-melody.input', :durable => true)
input_channel.confirm_select

handle, since_id = store.transaction { [store[:handle], store.fetch(:since_id, 1)] }
loop do
  puts " [*] Fetching with since_id = #{since_id}"
  tweets = Retryable.retryable(:tries => 15, :sleep => lambda { |n| 2 ** (n + 1) }) do
    client.mentions_timeline(:since_id => since_id, :count => 200).sort_by { |tweet| tweet.id }
  end
  tweets.each do |tweet|
    text = tweet.text.sub(handle, '').lstrip
    msg = { :id => tweet.id, :user => tweet.user.screen_name, :text => text }.to_edn
    input_queue.publish(msg, :persistent => true)
    success = input_channel.wait_for_confirms
    if success
      puts " [x] Sent #{msg}"
    else
      puts " [ ] Message nacked, will retry (#{msg})"
      break
    end
    since_id = tweet.id
    store.transaction { store[:since_id] = since_id }
    puts " [*] Set since_id = #{since_id}"
  end
  sleep 65
end
