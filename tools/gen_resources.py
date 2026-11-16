#!/usr/bin/env python3
"""Regenerates src/main/resources from a Monkeytype source tree + fonts.
usage: gen_resources.py <monkeytype-src-dir> <fonts-dir> [<fonts-dir> ...]"""
import json, os, re, shutil, sys, random, glob

MT = sys.argv[1]
FONT_DIRS = sys.argv[2:]
RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
STATIC = os.path.join(MT, 'frontend', 'static')

def fresh(d):
    p = os.path.join(RES, d)
    shutil.rmtree(p, ignore_errors=True)
    os.makedirs(p)
    return p

# ---- themes (defined in themes.ts since v26) ----
src = open(os.path.join(MT, 'frontend/src/ts/constants/themes.ts'), encoding='utf8').read()
start = src.index('export const themes')
themes = []
for m in re.finditer(r'^\s{2}"?([A-Za-z0-9_]+)"?:\s*\{(.*?)^\s{2}\},', src[start:], re.S | re.M):
    body = m.group(2)
    t = {'name': m.group(1)}
    for k, v in re.findall(r'(\w+):\s*"(#[0-9a-fA-F]+)"', body):
        t[k] = v
    t['hasCss'] = 'hasCss: true' in body
    themes.append(t)
d = fresh('themes')
json.dump(themes, open(os.path.join(d, 'themes.json'), 'w'), indent=0)
print('themes', len(themes))

# ---- british english replacements ----
be = open(os.path.join(MT, 'frontend/src/ts/constants/british-english.ts'), encoding='utf8').read()
pairs = dict(re.findall(r'^\s{2}"?([A-Za-z\-\']+)"?:\s*"([A-Za-z\-\']+)",?$', be, re.M))
d = fresh('others')
json.dump(pairs, open(os.path.join(d, 'british-english.json'), 'w'))
print('british english', len(pairs))

# ---- plain copies ----
def copy_tree(src_dir, dst, exts):
    d = fresh(dst)
    n = 0
    for root, _, files in os.walk(src_dir):
        for f in files:
            if f.lower().endswith(exts):
                rel = os.path.relpath(os.path.join(root, f), src_dir)
                out = os.path.join(d, rel)
                os.makedirs(os.path.dirname(out), exist_ok=True)
                shutil.copy(os.path.join(root, f), out)
                n += 1
    print(dst, n)

copy_tree(os.path.join(STATIC, 'languages'), 'languages', ('.json',))
copy_tree(os.path.join(STATIC, 'quotes'), 'quotes', ('.json',))
copy_tree(os.path.join(STATIC, 'layouts'), 'layouts', ('.json',))
copy_tree(os.path.join(STATIC, 'sounds'), 'sounds', ('.wav',))

d = fresh('fonts')
n = 0
for fd in FONT_DIRS:
    for f in glob.glob(os.path.join(fd, '*.ttf')) + glob.glob(os.path.join(fd, '*.otf')):
        shutil.copy(f, d); n += 1
print('fonts', n)

# ---- tutor lessons ----
words_src = []
for lang in ('english_1k', 'english_5k'):
    p = os.path.join(STATIC, 'languages', lang + '.json')
    if os.path.exists(p):
        words_src += json.load(open(p, encoding='utf8'))['words']
BAD = {'kill','killed','drug','drugs','sex','sexual','dead','death','died','murder','rape','porn','naked','suicide','hell','damn'}
words_src = [w for w in dict.fromkeys(words_src) if w.isalpha() and w.isascii() and w == w.lower() and w not in BAD]
rnd = random.Random(7)

def words_using(letters, n, lo=2, hi=7):
    ok = [w for w in words_src if lo <= len(w) <= hi and set(w) <= set(letters)]
    rnd.shuffle(ok)
    return ok[:n]

def pairs(a, b, reps=3):
    out = []
    for x in (a, b):
        for y in (a, b):
            out.append((x + y + x + ' ') * 1)
    return ' '.join(out * reps)

def wrap_lines(text, width=60):
    words, lines, cur = text.split(), [], ''
    for w in words:
        if len(cur) + len(w) + 1 > width and cur:
            lines.append(cur); cur = w
        else:
            cur = (cur + ' ' + w).strip()
    if cur: lines.append(cur)
    return '\n'.join(lines)

def write_lesson(level, num, title, instruction, exercise):
    base = os.path.join(RES, 'tutor', level)
    os.makedirs(base, exist_ok=True)
    slug = re.sub(r'[^a-z0-9]+', '-', title.lower()).strip('-')
    with open(os.path.join(base, '%02d_%s.txt' % (num, slug)), 'w', encoding='utf8') as f:
        for line in instruction.strip().split('\n'):
            f.write('# ' + line.strip() + '\n')
        f.write(wrap_lines(exercise) + '\n')

shutil.rmtree(os.path.join(RES, 'tutor'), ignore_errors=True)
HOME = 'asdfghjkl'

# BASIC: posture, home row
write_lesson('basic', 1, 'Posture and home position',
 'Sit up straight with your feet flat on the floor and your wrists relaxed, not resting on the desk edge.\nPlace the left fingers on A S D F and the right fingers on J K L ;. Your thumbs rest lightly on the space bar.\nFeel the small bumps on F and J: they tell your index fingers where home is.',
 'fff jjj fff jjj ddd kkk ddd kkk sss lll sss lll aaa ;;; aaa ;;; fj fj dk dk sl sl a; a; ' * 2)
write_lesson('basic', 2, 'Left hand home row',
 'Use one finger per key: little finger A, ring finger S, middle finger D, index finger F.\nPress each key with a light, quick tap and return the finger to its home key.',
 ' '.join(['asdf fdsa sdfa dsaf afsd fads sadf dfas', 'aa ss dd ff as sa ds sd df fd af fa', 'fad sad dad add fads sass dass fass']) * 2)
write_lesson('basic', 3, 'Right hand home row',
 'Right hand: index finger J, middle finger K, ring finger L, little finger ;.\nKeep your wrist level and let each finger do its own work.',
 ' '.join(['jkl; ;lkj kl;j lj;k j;kl lkj; ;jkl', 'jj kk ll ;; jk kj kl lk l; ;l jl lj', 'jkl kll lkj jlk lkl kjk']) * 2)
write_lesson('basic', 4, 'Both hands and the space bar',
 'Alternate hands. Press the space bar with whichever thumb feels natural, then return it to rest.\nSay the letters quietly to yourself as you type; this builds the link between sight and touch.',
 'fj dk sl a; fj dk sl a; ad ad sk sk fl fl a; a; jf kd ls ;a jf kd ls ;a sad lad ask fall dad flask lads salad ' * 2)
write_lesson('basic', 5, 'Reaching for G and H',
 'The index fingers each cover two columns. Left index: F and G. Right index: J and H.\nReach sideways, tap, and bring the finger straight back to F or J.',
 pairs('f', 'g') + ' ' + pairs('j', 'h') + ' gh hg fg gf jh hj ' + ' '.join(words_using(HOME, 24, 3, 6)))
write_lesson('basic', 6, 'Home row words',
 'Everything on the home row so far. Keep a steady rhythm; speed will come later.\nAim for accuracy first: a clean lesson is better than a fast one.',
 ' '.join(words_using(HOME, 60, 3, 7)))

# BEGINNER: add letters step by step
learned = HOME
steps = [('E and I', 'ei', 'Middle finger reaches up for E (left) and I (right). The fingers return to D and K after each key.'),
         ('R and U', 'ru', 'Index fingers reach up: left index for R, right index for U. Return to F and J.'),
         ('T and Y', 'ty', 'Index fingers reach up and across: left index for T, right index for Y.'),
         ('W and O', 'wo', 'Ring fingers reach up: left ring for W, right ring for O.'),
         ('Q and P', 'qp', 'Little fingers reach up: left little finger for Q, right little finger for P.'),
         ('V and N', 'vn', 'Index fingers reach down: left index for V, right index for N.'),
         ('C and M', 'cm', 'Middle fingers reach down: left middle for C, right middle for M.'),
         ('X and Z', 'xz', 'Ring and little finger reach down: left ring for X, left little finger for Z.'),
         ('B', 'b', 'Left index reaches down and across for B. Keep the right hand at rest.')]
num = 1
for title, add, ins in steps:
    learned += add
    ex = pairs(add[0], add[-1]) if len(add) > 1 else (add * 3 + ' ') * 6
    ex += ' ' + ' '.join(words_using(learned, 50, 3, 7))
    write_lesson('beginner', num, 'New keys ' + title, ins + '\nWords below use every key you have learned so far.', ex)
    num += 1
write_lesson('beginner', num, 'All letters: short words',
 'You now know every letter. Type these short words without looking at the keyboard.',
 ' '.join(words_using('abcdefghijklmnopqrstuvwxyz', 70, 2, 5)))
num += 1
write_lesson('beginner', num, 'All letters: longer words',
 'Longer words build rhythm. Keep your eyes on the screen and let your fingers find the keys.',
 ' '.join(words_using('abcdefghijklmnopqrstuvwxyz', 45, 5, 9)))
num += 1
write_lesson('beginner', num, 'Beginner review',
 'A mix of everything from the beginner level. Take it slowly and aim for 95 percent accuracy.',
 ' '.join(words_using('abcdefghijklmnopqrstuvwxyz', 80, 3, 8)))

SENT = ["The old clock on the wall kept perfect time.", "She walked along the river before the sun came up.",
 "We planned the trip carefully and packed light.", "A small boat drifted past the harbour at noon.",
 "He read the instructions twice and then began.", "The market was busy with people buying fruit and bread.",
 "Please close the window before you leave the room.", "Their garden was full of tall yellow flowers.",
 "It takes patience to learn something new.", "The train arrives at nine and leaves at ten past.",
 "They shared a quiet meal and talked about the week.", "Good habits are built one small step at a time.",
 "The library opens early on weekday mornings.", "Our team finished the report ahead of the deadline.",
 "A light rain began to fall as we reached the hill."]
CAPS = ["Time", "People", "Water", "Light", "Morning", "River", "Garden", "Market", "Library", "Station", "Harbour", "Kitchen",
        "Winter", "Summer", "Autumn", "Spring", "Monday", "Friday", "London", "Paris", "Berlin", "Tokyo", "Cairo", "Delhi"]

# INTERMEDIARY
write_lesson('intermediary', 1, 'Capital letters with Shift',
 'Hold Shift with the little finger of the hand that is NOT typing the letter. Left Shift for right-hand letters, right Shift for left-hand letters.\nRelease Shift straight away after the capital.',
 ' '.join(CAPS * 2))
write_lesson('intermediary', 2, 'Period and comma',
 'The comma is typed with the right middle finger, the period with the right ring finger.',
 ' '.join(['the day was long, but we finished.', 'first, second, third, fourth.', 'slow, steady, calm.', 'one. two. three. four.'] * 3))
write_lesson('intermediary', 3, 'Apostrophe and quotation marks',
 'The apostrophe sits next to the semicolon, under the right little finger. Hold Shift (left) for the quotation mark.',
 ' '.join(["it's", "don't", "we'll", "they're", "I'm", "isn't", "can't", "she's", '"yes"', '"no"', '"maybe"', '"well done"'] * 4))
write_lesson('intermediary', 4, 'Question and exclamation marks',
 'The question mark is Shift plus the slash key. The exclamation mark is Shift (right) plus 1.',
 ' '.join(["Is it ready?", "Who is there?", "What time is it?", "Well done!", "Look out!", "Not again!", "Can you hear me?", "How are you?"] * 2))
write_lesson('intermediary', 5, 'Colon, semicolon and hyphen',
 'Semicolon: right little finger. Colon: Shift plus semicolon. Hyphen: right little finger on the number row.',
 ' '.join(["note: bring water;", "well-known; low-cost; part-time;", "ratio: three-to-one;", "time: late-night;", "list: red; blue; green;"] * 3))
write_lesson('intermediary', 6, 'Short sentences', 'Full sentences with capitals and punctuation. Keep a steady rhythm.', ' '.join(SENT[:8]))
write_lesson('intermediary', 7, 'Common words',
 'The most common English words appear again and again. Fast, accurate typing of these gives the biggest speed gain.',
 ' '.join(words_src[:70]))
write_lesson('intermediary', 8, 'Longer words',
 'Longer words are typed best in a steady flow rather than key by key.',
 ' '.join(words_using('abcdefghijklmnopqrstuvwxyz', 40, 8, 12)))
write_lesson('intermediary', 9, 'Sentences with everything', 'Capitals, commas, apostrophes and full stops together.', ' '.join(SENT[7:]))
write_lesson('intermediary', 10, 'Intermediary review',
 'A final mixed lesson. Aim for steady speed and very few mistakes.',
 "Don't rush, but don't stop either. A smooth rhythm beats short bursts of speed. " + ' '.join(SENT[:5]))

# ADVANCED
write_lesson('advanced', 1, 'Number row 1 to 5',
 'Left hand: little finger 1, ring finger 2, middle finger 3, index finger 4 and 5. Look at the screen, not the keys.',
 ' '.join(['12345 54321 13524 24153', '11 22 33 44 55 12 23 34 45 51', '3 apples 4 pears 5 plums 2 figs 1 lime'] * 3))
write_lesson('advanced', 2, 'Number row 6 to 0',
 'Right hand: index finger 6 and 7, middle finger 8, ring finger 9, little finger 0.',
 ' '.join(['67890 09876 68790 79680', '66 77 88 99 00 67 78 89 90 06', '7 days 8 hours 9 minutes 60 seconds 100 years'] * 3))
write_lesson('advanced', 3, 'Mixed numbers and words',
 'Numbers inside everyday text: dates, prices and quantities.',
 'On 12 March 2024 we sold 348 tickets at 15 each, 62 more than the 286 sold in 2023. Room 407 holds 36 seats; room 512 holds 118. ' * 2)
write_lesson('advanced', 4, 'Shifted symbols for 1 to 5',
 'Hold the opposite Shift. The symbols sit above the numbers 1 to 5: left hand, so use right Shift.',
 ' '.join(['!@#$% %$#@! !!! @@@ ### $$$ %%%', 'email@site.com #tag $25 50% !done', 'user@mail.org #plan $300 75% !ready'] * 3))
write_lesson('advanced', 5, 'Shifted symbols for 6 to 0',
 'Symbols above 6 to 0, plus underscore and plus. Use left Shift for the right-hand symbols.',
 ' '.join(['^&*() )(*&^ ^^^ &&& *** ((( )))', 'a+b=c (a+b)*(c+d) x_y_z 5*(6+7) &&', '2^8 = 256 (a&b) n*(n+1)/2 snake_case_name'] * 3))
write_lesson('advanced', 6, 'Brackets and slashes',
 'Brackets and braces are typed with the right little finger. Slashes use the right little finger too.',
 ' '.join(['[ ] { } ( ) < > / \\ | ?', 'list[0] map{key} (note) <tag> path/to/file C:\\temp\\log', 'if (x > 0) { y = list[x]; } // done'] * 3))
write_lesson('advanced', 7, 'Accuracy drills',
 'Words that look alike are easy to mistype. Slow down and read each word fully before typing it.',
 ' '.join(['their there they\'re', 'form from farm', 'trial trail trial', 'quite quiet quit', 'affect effect', 'then than', 'lose loose', 'from form'] * 4))
write_lesson('advanced', 8, 'Speed building',
 'Type each line at a comfortable speed, then repeat it slightly faster. Stay accurate.',
 ' '.join(words_src[:40] * 3))
write_lesson('advanced', 9, 'Long sentences',
 'Longer sentences with several clauses and punctuation marks.',
 "When the morning mail finally arrived, after a long wait at the door, she opened the largest envelope first, read the letter twice, and then, smiling quietly, began to write a reply. " * 2)
write_lesson('advanced', 10, 'Advanced review',
 'Everything together: capitals, punctuation, numbers and symbols.',
 'Invoice #4821 (due 30 June): 12 units at $45.50 = $546.00, plus 8% tax. Contact: accounts@example.org; phone 555-0147. Thank you!' * 2)

# PROFESSIONAL
write_lesson('professional', 1, 'Office email',
 'Typical business correspondence. Keep a calm, even pace and check punctuation as you go.',
 "Dear Ms. Alvarez, Thank you for your message of 14 May. I have reviewed the proposal with my team, and we are pleased to confirm the revised schedule: kick-off on 3 June, first delivery on 24 June, final review on 15 July. Please let me know if any dates need adjusting. Kind regards, Daniel Okoye")
write_lesson('professional', 2, 'Meeting notes',
 'Notes mix short phrases, numbers and abbreviations.',
 "Meeting notes, Tuesday 10:30. Attendees: Priya, Tom, Mei, Jonas. 1) Budget: approved at $42,500; Tom to send the breakdown by Friday. 2) Hiring: two roles open, interviews start next week. 3) Risks: supplier delay (about 5 days); Mei will confirm alternatives. Next meeting: same time, 16 June.")
write_lesson('professional', 3, 'Technical writing',
 'Precise terms, long words and careful punctuation.',
 "A well-designed cache stores frequently requested results so that repeated requests can be answered without recomputation. The trade-off is memory: a larger cache improves the hit rate, but it also increases the cost of invalidation, which must occur whenever the underlying data changes.")
write_lesson('professional', 4, 'Code: Java',
 'Programming text uses many symbols. Type each bracket and semicolon exactly.',
 "public static int sum(int[] values) { int total = 0; for (int i = 0; i < values.length; i++) { total += values[i]; } return total; } // returns 0 for an empty array")
write_lesson('professional', 5, 'Data entry',
 'Numbers, codes and dates in dense rows. Accuracy matters most.',
 "A-1042 2024-03-18 $1,250.00 B-2217 2024-03-19 $98.40 C-3305 2024-03-21 $4,075.25 D-4410 2024-03-22 $312.90 E-5521 2024-03-25 $27.15 F-6638 2024-03-26 $1,999.99 G-7746 2024-03-27 $640.00")
write_lesson('professional', 6, 'Story: the lighthouse',
 'Flowing prose with dialogue. Let the rhythm of the sentences carry you.',
 "The keeper climbed the last step and paused to catch his breath. \"Is it clear tonight?\" his daughter asked from below. \"Clear enough,\" he said, \"but the wind is turning; we should keep the lamp bright until dawn.\" Far out on the dark water, a single light answered, and both of them smiled.")
write_lesson('professional', 7, 'Formal report',
 'Formal language with numbers and percentages.',
 "Overall customer satisfaction rose from 71% to 84% between January and September, while average response time fell by 38 seconds. The largest improvement, 19 points, came from the new support portal; the smallest, 4 points, came from printed documentation.")
write_lesson('professional', 8, 'Long passage',
 'The final test: a long passage. Maintain accuracy over the whole text, and keep your shoulders relaxed.',
 "Learning to type well is mostly a matter of patience. At first every key is a small decision, and every word takes effort. Gradually the decisions fade into habit: the fingers know where to go, the eyes stay on the text, and the mind is free to think about what is being said rather than how it is being typed. That is the real goal of touch typing, and it arrives sooner than most people expect if they practise a little every day, keep their accuracy high, and resist the urge to look down at the keyboard.")
print('lessons', sum(len(f) for _, _, f in os.walk(os.path.join(RES, 'tutor'))))
