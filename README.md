# speech-melody

This repository contains the source code of the following bot:

![bot screenshot](https://raw.githubusercontent.com/PawelStroinski/speech-melody/master/images/screenshot.png)

All it does is convert text pronounciacion to a pseudo-melody using mel-frequency cepstral coefficients (MFCCs).

## Installation

This is the complete installation procedure from the vanilla Ubuntu 15.04 x64 box to the running bot. No sound card is required, 1 GB RAM is recommended.

    apt-get update
    apt-get install jack-tools # enable realtime
    apt-get install alsa-utils
    apt-get install alsa
    apt-get install dbus-x11
    modprobe snd-dummy
    dbus-launch jackd -d alsa -d default -r 44100 &
    apt-get install openjdk-7-jdk
    cd /usr/local/bin && wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && chmod +x lein && cd ~ && lein
    add-apt-repository ppa:supercollider/ppa
    apt-get update
    apt-get install supercollider supercollider-gedit
    scsynth -u 57110 &
    #now test: (use 'overtone.core)(connect-external-server 57110)(use 'overtone.inst.sampled-piano)
    apt-get install build-essential
    apt-get install ruby
    apt-get install ruby-dev
    apt-get install rabbitmq-server
    apt-get install upstart
    apt-get install upstart-sysv
    reboot
    #now copy & extract or checkout speech-melody in /usr/local/speech-melody dir
    mv install/speech-melody.conf /etc/init
    mv install/speech-melody-bot.conf /etc/init
    start speech-melody
    start speech-melody-bot
    #to view logs: cat /var/log/upstart/speech-melody.log and cat /var/log/upstart/speech-melody-bot.log
    #also cat /usr/local/speech-melody/speech-melody.log and cat /usr/local/speech-melody/ruby_twitter_bot/ruby_twitter_bot.log

## Credits

![credits screenshot](https://raw.githubusercontent.com/PawelStroinski/speech-melody/master/images/credits.png)

## License

Copyright © 2015 Paweł Stroiński

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
