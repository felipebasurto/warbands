"""Synthesizes the trailer theme: a short medieval war march in D minor, 28 s, written for the trailer's cuts.
No samples and no generative model: every sound is additive or noise synthesis (strings, brass, war drums, cymbal) and the
melody and chords are written below, so the result is original and free of third-party licences.

Run from this folder:  python3 trailer_music.py      (needs numpy and scipy)
Writes ../publish/trailer/warbands-theme.wav (stereo, 44.1 kHz). Bars are 2.5 s (96 bpm), laid out against the trailer:
  bar 0      opening card, drone
  bars 1-3   "Raise an army"
  bars 4-6   "Fight"
  bars 7-9   "Conquer"
  bar 10     end card, final chord
"""
import os
import numpy as np
from scipy.signal import butter, lfilter, fftconvolve
from scipy.io import wavfile

SR = 44100
BEAT = 60.0 / 96.0
BAR = BEAT * 4
TOTAL = 28.0
rng = np.random.default_rng(7)
N = int(TOTAL * SR)
T = np.arange(N) / SR
LEFT = np.zeros(N)
RIGHT = np.zeros(N)


def hz(midi):
	return 440.0 * 2 ** ((midi - 69) / 12.0)


def lowpass(x, cutoff, order=2):
	b, a = butter(order, min(cutoff, SR / 2 - 100) / (SR / 2), 'low')
	return lfilter(b, a, x)


def highpass(x, cutoff, order=2):
	b, a = butter(order, cutoff / (SR / 2), 'high')
	return lfilter(b, a, x)


def env(n, attack, release, hold_to=None):
	e = np.ones(n)
	a = max(1, int(attack * SR))
	r = max(1, int(release * SR))
	e[:a] = np.linspace(0, 1, a) ** 1.5
	r = min(r, n)
	e[n - r:] *= np.linspace(1, 0, r) ** 1.5
	return e


def add(buf_l, buf_r, t0, sig, gain=1.0, pan=0.0):
	i = int(t0 * SR)
	if i >= N:
		return
	sig = sig[:N - i]
	buf_l[i:i + len(sig)] += sig * gain * np.cos((pan + 1) * np.pi / 4)
	buf_r[i:i + len(sig)] += sig * gain * np.sin((pan + 1) * np.pi / 4)


def saw(freq, dur, harmonics=40, detune=0.0, vibrato=0.0):
	t = np.arange(int(dur * SR)) / SR
	f = freq * (1 + detune) * (1 + vibrato * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.6))
	phase = 2 * np.pi * np.cumsum(f) / SR
	out = np.zeros_like(t)
	for k in range(1, harmonics + 1):
		if f.max() * k > SR / 2 - 1000:
			break
		out += np.sin(k * phase) / k
	return out * 0.55


def strings(freq, dur, bright=3200):
	"""Three detuned saws through a low-pass: a section of bowed strings."""
	s = sum(saw(freq, dur, detune=d, vibrato=0.003) for d in (-0.004, 0.0, 0.004)) / 3
	return lowpass(s, bright) * env(len(s), 0.12, 0.18)


def brass(freq, dur, push=1.0):
	"""A saw whose brightness swells with the note, like a horn section."""
	s = saw(freq, dur, vibrato=0.002)
	n = len(s)
	bright = lowpass(s, 2600 * push) * np.linspace(0.4, 1.0, n) + lowpass(s, 900) * 0.5
	return bright * env(n, 0.06, 0.12) * 0.8


def pad(freq, dur):
	t = np.arange(int(dur * SR)) / SR
	s = np.sin(2 * np.pi * freq * t) + 0.5 * np.sin(2 * np.pi * freq * 2.003 * t) + 0.25 * np.sin(2 * np.pi * freq * 3 * t)
	return s * env(len(t), 1.2, 1.5) * 0.4


def drum(freq, dur=0.9, power=1.0):
	t = np.arange(int(dur * SR)) / SR
	sweep = freq * (1 + 1.2 * np.exp(-t * 18))
	body = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.exp(-t * 4.2)
	skin = lowpass(rng.standard_normal(len(t)), 600) * np.exp(-t * 22) * 0.5
	return (body + skin) * power


def snare(dur=0.22, power=1.0):
	t = np.arange(int(dur * SR)) / SR
	noise = highpass(rng.standard_normal(len(t)), 1500) * np.exp(-t * 20)
	tone = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 28) * 0.4
	return (noise * 0.7 + tone) * power


def cymbal(dur=3.0, power=1.0):
	t = np.arange(int(dur * SR)) / SR
	return highpass(rng.standard_normal(len(t)), 5000) * np.exp(-t * 1.6) * power * 0.35


# ---- harmony: one chord per bar (a bar with two entries changes at its middle) ----
D, E, F, G, A, Bb, C = 2, 4, 5, 7, 9, 10, 0
CHORDS = {  # midi roots (octave 2) and the chord tones above them
	'Dm': (38, [0, 7, 12, 15]), 'Bb': (34, [0, 7, 12, 16]), 'C': (36, [0, 7, 12, 16]), 'Gm': (31, [0, 7, 12, 15]),
	'A': (33, [0, 7, 12, 16]), 'D': (38, [0, 7, 12, 16]),
}
BARS = ['Dm', 'Dm', 'Bb', 'C', 'Dm', 'Bb', 'Gm', 'A', 'Dm', ('Bb', 'C'), 'D']

# ---- melody in (beat in bar, midi, beats) ----
MELODY = {
	1: [(0, 62, 1.5), (1.5, 69, 0.5), (2, 74, 2)],
	2: [(0, 72, 1), (1, 70, 1), (2, 69, 1), (3, 67, 1)],
	3: [(0, 65, 1), (1, 67, 1), (2, 69, 2)],
	4: [(0, 74, 2), (2, 77, 1), (3, 76, 1)],
	5: [(0, 74, 1.5), (1.5, 72, 0.5), (2, 70, 2)],
	6: [(0, 69, 1), (1, 70, 1), (2, 67, 2)],
	7: [(0, 69, 3), (3, 70, 1)],
	8: [(0, 74, 1.5), (1.5, 77, 0.5), (2, 81, 2)],
	9: [(0, 79, 1), (1, 77, 1), (2, 76, 1), (3, 74, 1)],
	10: [(0, 74, 3.0)],
}


def chord_at(bar, beat):
	c = BARS[bar]
	return c if isinstance(c, str) else c[0 if beat < 2 else 1]


def build():
	global LEFT, RIGHT
	pad_l, pad_r = np.zeros(N), np.zeros(N)
	# bar 0: a low drone and a swell, then the pad follows the harmony
	add(LEFT, RIGHT, 0, strings(hz(38), 3.4, 1400), 0.5)
	add(LEFT, RIGHT, 0, strings(hz(45), 3.4, 1400), 0.35)
	for bar in range(len(BARS)):
		for half in (0, 2):
			name = chord_at(bar, half)
			root, tones = CHORDS[name]
			t0 = bar * BAR + half * BEAT
			dur = (2 if isinstance(BARS[bar], tuple) else 4) * BEAT + 0.8
			for k, tone in enumerate(tones[1:], 1):
				add(pad_l, pad_r, t0, pad(hz(root + 24 + tone), dur), 0.16, pan=-0.5 + 0.35 * k)
			if not isinstance(BARS[bar], tuple):
				break
	# string ostinato in eighths from bar 1: root, fifth, octave, fifth
	for bar in range(1, len(BARS) - 1):
		loud = 0.5 + 0.12 * (bar >= 4) + 0.1 * (bar >= 7)
		for step in range(8):
			beat = step * 0.5
			name = chord_at(bar, beat)
			root, tones = CHORDS[name]
			note = root + 12 + [0, 7, 12, 7][step % 4]
			add(LEFT, RIGHT, bar * BAR + beat * BEAT, strings(hz(note), BEAT * 0.5 * 1.15, 2200) * (1.25 if step % 4 == 0 else 0.8), loud * 0.45, pan=-0.35)
	# a held low string line under the whole piece
	for bar in range(len(BARS)):
		for half in ((0, 2) if isinstance(BARS[bar], tuple) else (0,)):
			name = chord_at(bar, half)
			root, _ = CHORDS[name]
			dur = (2 if isinstance(BARS[bar], tuple) else 4) * BEAT + 0.4
			add(LEFT, RIGHT, bar * BAR + half * BEAT, strings(hz(root), dur, 900), 0.4, pan=0.0)
	# brass melody, doubled in octaves for the later sections
	for bar, notes in MELODY.items():
		for beat, midi, length in notes:
			t0 = bar * BAR + beat * BEAT
			dur = length * BEAT * 0.97
			push = 1.0 + 0.4 * (bar >= 4)
			add(LEFT, RIGHT, t0, brass(hz(midi), dur, push), 0.55 + 0.1 * (bar >= 4), pan=0.25)
			if bar >= 4:
				add(LEFT, RIGHT, t0, brass(hz(midi - 12), dur, 0.8), 0.4, pan=-0.2)
			if bar >= 7:
				add(LEFT, RIGHT, t0, brass(hz(midi - 5), dur, 0.8), 0.28, pan=0.0)
	# war drums
	def hit(bar, beat, freq, power):
		add(LEFT, RIGHT, bar * BAR + beat * BEAT, drum(freq, 1.1, power), 0.9, pan=0.0)
	hit(0, 3.5, 73, 0.9)
	for bar in range(1, len(BARS)):
		low, high = 73, 98
		if bar in (1, 2):
			for b in (0, 2):
				hit(bar, b, low, 1.0)
			if bar == 2:
				hit(bar, 3.5, high, 0.7)
		elif bar == 3:
			for b, f in ((0, low), (1.5, high), (2, low), (3, high), (3.5, high)):
				hit(bar, b, f, 0.9)
		elif bar in (4, 5, 6, 8, 9):
			for b in (0, 1, 2, 3):
				hit(bar, b, low if b % 2 == 0 else high, 1.0 if b == 0 else 0.8)
			for b in (0.5 + 1.0 * i for i in range(4)):
				hit(bar, b, high, 0.35)
			for b in (1, 3):
				add(LEFT, RIGHT, bar * BAR + b * BEAT, snare(0.22, 0.8), 0.55, pan=0.15)
		elif bar == 7:
			for i in range(16):  # a snare roll that grows into the assault
				add(LEFT, RIGHT, bar * BAR + i * BEAT / 4, snare(0.15, 0.3 + 0.7 * i / 15), 0.55, pan=0.1)
			for b in (0, 1, 2, 3):
				hit(bar, b, 73, 0.9)
		elif bar == 10:
			hit(bar, 0, 73, 1.3)
			hit(bar, 0, 55, 1.0)
	add(LEFT, RIGHT, 7 * BAR, cymbal(2.6, 0.7), 0.8, pan=0.2)
	add(LEFT, RIGHT, 10 * BAR, cymbal(3.0, 1.0), 0.9, pan=-0.1)
	add(LEFT, RIGHT, 4 * BAR, cymbal(1.8, 0.45), 0.7, pan=0.2)
	# final chord: D major with the third and an octave of brass
	for midi in (50, 57, 62, 66, 69, 74):
		add(LEFT, RIGHT, 10 * BAR, brass(hz(midi), 2.9, 1.2), 0.45, pan=(midi - 62) / 40.0)
		add(LEFT, RIGHT, 10 * BAR, strings(hz(midi - 12), 3.0, 2000), 0.35, pan=-(midi - 62) / 60.0)
	LEFT += pad_l
	RIGHT += pad_r

	# hall reverb: decaying noise impulse, a bit different per channel
	def reverb(x, seed):
		r = np.random.default_rng(seed)
		length = int(2.4 * SR)
		ir = lowpass(r.standard_normal(length), 5000) * np.exp(-np.arange(length) / SR * 2.6)
		ir[:int(0.02 * SR)] *= np.linspace(0, 1, int(0.02 * SR))
		return fftconvolve(x, ir)[:N] * 0.045
	LEFT = LEFT + reverb(LEFT, 11)
	RIGHT = RIGHT + reverb(RIGHT, 12)
	out = np.stack([LEFT, RIGHT], axis=1)
	fade = np.clip((TOTAL - T) / 1.1, 0, 1)
	out *= fade[:, None] * np.clip(T / 0.25, 0, 1)[:, None]
	# the dynamic arc of the trailer: quiet opening, building through the fight, loudest in the assault
	points = [(0, 0.40), (2.5, 0.55), (3.5, 0.62), (10, 0.72), (12.5, 0.80), (17.5, 0.88), (20, 1.0), (25, 1.0), (28, 0.9)]
	curve = np.interp(T, [p[0] for p in points], [p[1] for p in points])
	out *= curve[:, None]
	out = out / (np.percentile(np.abs(out), 99.7) / 0.8)   # leave headroom, then a soft clip catches the drum peaks
	out = np.tanh(out)
	return out / np.max(np.abs(out)) * 0.89


if __name__ == '__main__':
	audio = build()
	target = os.path.join(os.path.dirname(__file__), '..', 'publish', 'trailer')
	os.makedirs(target, exist_ok=True)
	wavfile.write(os.path.join(target, 'warbands-theme.wav'), SR, (audio * 32767).astype(np.int16))
	print('wrote', os.path.join(target, 'warbands-theme.wav'), f'{TOTAL:.0f} s')
