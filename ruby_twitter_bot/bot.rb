#!/usr/bin/env ruby

require 'rubygems'
require 'bundler/setup'
require 'yaml/store'
require 'twitter'
require 'bunny'
require 'retryable'
require 'edn'
require 'logger'

store = YAML::Store.new('bot.yaml')
retryable_options = {:tries => 15, :sleep => lambda { |n| 2 ** (n + 1) }}
$logger = Logger.new('ruby_twitter_bot.log', 10, 1024 * 1024)

def log(*args)
  $logger.info *args
  puts *args
end

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

done_channel = connection.create_channel
done_queue = done_channel.queue('speech-melody.done', :durable => true)
log " <*> Waiting for messages in #{done_queue.name}"
done_queue.subscribe(:manual_ack => true) do |delivery_info, properties, body|
  log " <x> Received #{body}"
  msg = EDN.read(body)
  tweet = Retryable.retryable(retryable_options) do
    client.update("Hey @#{msg[:user]}! There you go: #{msg[:url]}", :in_reply_to_status_id => msg[:id])
  end
  log " <x> Tweeted '#{tweet.text}' that has id = #{tweet.id}"
  done_channel.ack(delivery_info.delivery_tag)
end

todo_channel = connection.create_channel
todo_queue = todo_channel.queue('speech-melody.todo', :durable => true)
todo_channel.confirm_select
handle, since_id = store.transaction { [store[:handle], store.fetch(:since_id, 1)] }
loop do
  log " [*] Fetching Twitter with since_id = #{since_id}"
  tweets = Retryable.retryable(retryable_options) do
    client.mentions_timeline(:since_id => since_id, :count => 200).sort_by { |tweet| tweet.id }
  end
  tweets.each do |tweet|
    text = tweet.text.sub(/#{Regexp.escape(handle)}/i, '').gsub(/ +/, ' ').strip
    msg = { :id => tweet.id, :user => tweet.user.screen_name, :text => text }.to_edn
    todo_queue.publish(msg, :persistent => true)
    success = todo_channel.wait_for_confirms
    if success
      log " [x] Sent #{msg} to #{todo_queue.name}"
    else
      log " [ ] Message nacked, will retry (#{msg})"
      break
    end
    since_id = tweet.id
    store.transaction { store[:since_id] = since_id }
    log " [*] Set since_id = #{since_id}"
  end
  sleep 65
end
