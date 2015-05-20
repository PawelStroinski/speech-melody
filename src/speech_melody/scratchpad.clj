(ns speech-melody.scratchpad)

(comment
  ((map->PlayableSample speech))
  (buffer-id speech)

  (demo 5
        (buf-rd 1 speech (+ (* (sin-osc 0.05) (buf-frames speech) (/ 1 speech-rate))
                            (sin-osc 200)
                            (square 10000))))

  (demo 5 (buf-rd 1 speech (* (lf-noise1 1) (buf-frames speech))))
  (demo 5 (buf-rd 1 speech (* (lin-lin (sin-osc speech-rate) -1 1 0 1) (buf-frames:ir speech))))

  (def buf (buffer 4096))
  (demo 5
        (let [rate 5
              src (play-buf 1 speech speech-rate)
              freqs (fft
                      buf src 0.1)
              filtered (pv-rand-comb freqs 0.8 (impulse:kr rate))]
          (pan2 (ifft filtered))))

  (demo 5
        (let [in (* [1 2] (play-buf 1 speech speech-rate :loop 1))
              chain (fft (local-buf 2048 2) in)
              chain (pv-brick-wall chain (sin-osc:kr [0.01 1]))
              chain (pv-rand-comb chain (sin-osc:kr [100 10]) (impulse:kr 10))]
          (ifft chain)))

  (demo
    (let [src (play-buf 1 speech speech-rate)
          freq (concat (range 900 0 -100) (range 50 0 -5))
          mul (range 0.05 1.0 0.05)
          osc (map #(* src (sin-osc %1) %2) freq mul)
          synth (+ (apply + osc) (* 10 (bpf src 100)))]
      [synth synth]))

  (demo
    (let [src (play-buf 1 speech speech-rate)
          synth (+ (impulse:ar src)
                   (* (pink-noise) src))]
      [synth synth]))
  )

(comment
  (demo (moog-ladder (play-buf 1 speech speech-rate) (* 10000 (lin-lin (sin-osc 10)))))

  (odoc pan2)

  (defsynth feedback-loop []
            (let [input (play-buf 1 speech speech-rate)
                  fb-in (local-in 1)
                  snd (+ input (leak-dc (delay-n fb-in 2.0 (* 0.8 (mouse-x 0.001 1.05)))))
                  fb-out (local-out snd)
                  snd (limiter snd 0.8)]
              (out 0 (pan2 snd))))

  (feedback-loop)
  (stop)
  )

(comment
  (scope)
  (spectrogram)
  (def increasing (freesound 188020))
  (increasing)
  )

(comment
  (def my-bus (audio-bus))

  ;; produce a signal & put it into the bus
  (defsynth sig-gen [out-bus 100]
            (let [sig (play-buf 1 speech speech-rate :action FREE)]
              (out out-bus sig)))

  ;; something to read the bus & make it audible
  ;(defsynth send-out [in-bus 3]
  ;          (let [src (in in-bus)]
  ;            (out 0 src)))

  ;; switch it on
  ;(send-out [:tail 0] my-bus)

  (sig-gen my-bus)

  (spectrogram :bus my-bus)
  )