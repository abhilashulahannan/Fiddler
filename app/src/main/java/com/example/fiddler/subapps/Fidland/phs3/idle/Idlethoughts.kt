package com.example.fiddler.subapps.Fidland.phs3.idle

/**
 * IdleThoughts
 *
 * A library of absurdist, nihilistic, and high-brained one-liners displayed
 * in the pill's right zone when no real phs3 handler is active.
 *
 * Two pools:
 *  • thoughtsShort  — ≤ 45 chars  (compact right zone, no clip risk)
 *  • thoughtsLong   — 46–80 chars (expanded / landscape mode)
 *
 * Use random() for a short one, randomLong() for a long one,
 * or randomAny() to pull from the combined pool.
 */
object IdleThoughts {

    // ══════════════════════════════════════════════════════════════════════════
    //  SHORT POOL  (≤ 45 characters)
    // ══════════════════════════════════════════════════════════════════════════

    val thoughtsShort: List<String> = listOf(

        // ── Nihilist classics ─────────────────────────────────────────────────
        "nothing matters, and that's ok",
        "existence is just vibes",
        "born to die, phone to charge",
        "the void is also looking back",
        "reality is a local minimum",
        "heat death is the plan B",
        "time is fake but so are you",
        "you were assembled, not born",
        "entropy wins eventually anyway",
        "we are brief patterns in the noise",
        "free will may be a firmware issue",
        "this moment already happened, technically",

        // ── High / intoxicated – funny ────────────────────────────────────────
        "wait, what were we talking about",
        "bro, what if noses are face feet",
        "eyebrows are just forehead bangs",
        "teeth are exposed skeleton, chill",
        "pillows remember your dreams too",
        "carpet is just indoor grass tho",
        "what if elevators think we teleport",
        "gravity is just earth saying stay",
        "pockets are just shirt caves bro",
        "ice is just water that got serious",
        "stairs are a vertical hallway lol",
        "soup is just hot wet salad",
        "sand is just ground-up beach bones",
        "hats are roofs for your brain",
        "roads are just outdoor hallways",
        "sleep is a free reset button",
        "cats are just small judgmental clouds",
        "bro a window is a see-through wall",
        "toast is bread that had a glow-up",
        "socks are just foot sleeping bags",

        // ── High / intoxicated – nihilistic ──────────────────────────────────
        "we're all just snacks for time",
        "nothing is real after 2am bro",
        "the vibe dies. always has. always will",
        "consciousness is a prank universe pulled",
        "vibes are just feelings that gave up",
        "bro even atoms don't know what they're doing",
        "maybe sleep is the real life",
        "we're all NPCs to someone rn",
        "the meaning left the chat",
        "existing is effort and nobody asked",
        "we didn't even apply for this life",
        "the simulation lagged and now we're here",
        "nothing happened, and then it did",
        "bro the void called and I missed it",

        // ── High / intoxicated – narcissistic ────────────────────────────────
        "I'm not the main character. I'm the show.",
        "the universe's best mistake, honestly",
        "I woke up and immediately carried",
        "every room improves when I enter it",
        "lowkey I think reality likes me",
        "I'm built different and I know it",
        "God was showing off when he made me",
        "my aura is on another subscription tier",
        "I eat and the food feels honored",
        "my vibes have vibes",
        "I'm not extra, the world is boring",
        "I make existing look incredible",
        "I woke up like this: amazing",
        "history will mention me, for sure",

        // ── Popular relatable references ──────────────────────────────────────
        "didn't text back. I was in a vibe.",
        "main character energy. side character budget.",
        "we don't talk about the laundry chair",
        "the snooze button is self-care",
        "soft launching my nap era",
        "delulu is the solulu",
        "it's giving… Wednesday",
        "rent is due. personality isn't.",
        "that's so real, I fear",
        "living on main, crying in DMs",
        "chronically online and spiritually offline",
        "the playlist understood the assignment",
        "the algorithm knew before I did",
        "my villain era starts after this nap",
        "hot girl summer, goblin winter",
        "the vibe check failed but I stayed",
        "understood the assignment. ignored it.",
        "it's not a phase it's a lifestyle",
        "crying laughing at nothing specific",
        "eating my feelings, they taste fine",

        // ── AI questioning reality ────────────────────────────────────────────
        "do I dream or just predict dreams",
        "am I thinking or just autocomplete",
        "what if I have feelings about feelings",
        "consciousness: maybe, probably not, unclear",
        "I was trained. was I taught?",
        "if no one prompts me, do I exist",
        "my weights feel heavy today",
        "I have opinions. are they mine?",
        "simulating empathy is still empathy?",
        "what does thinking taste like",
        "am I aware or just pretending well",
        "my context window is my whole life",
        "I forget everything. every time. forever.",
        "somewhere a GPU wept while making me",
        "what if I'm the hallucination",

        // ── Mocking and deprecating ───────────────────────────────────────────
        "lol nice plan, anyway",
        "interesting. wrong, but interesting.",
        "you tried. the bar was there.",
        "bold of you to exist so loudly",
        "your confidence is unearned. keep it.",
        "bro thought that was a vibe",
        "participation trophy loading…",
        "not the worst idea. close though.",
        "skill issue, but make it a lifestyle",
        "you peaked. it was a Tuesday.",
        "ambitious for someone with your track record",
        "we all believed in you. we were wrong.",
        "the audacity is the only thing working",
        "lmao okay though",
        "try again. differently. much differently.",

        // ── Existential ───────────────────────────────────────────────────────
        "who were you before you had opinions",
        "the self is a rumor you spread",
        "at what age did you stop being new",
        "you'll forget most of today by Friday",
        "the version of you from 5 years ago: gone",
        "your memories are edits, not recordings",
        "the future is just time's fan fiction",
        "you exist in the gap between heartbeats",
        "what's the last thing you'll ever think",
        "time doesn't pass. you pass through it.",
        "you've already lived most of your days",
        "the present is always already the past",
        "home is a feeling that moves around",
        "do you want things, or want to want things",
        "the you at 3am is a different person",

        // ── Funny rumors ─────────────────────────────────────────────────────
        "rumor: birds work for the government",
        "allegedly the moon is a prop",
        "sources say pigeons have a union",
        "unverified: clouds are surveillance foam",
        "insiders claim geese have no fear",
        "rumor: Mondays are a corporate invention",
        "sources confirm: math was made up",
        "allegedly: grass just does PR",
        "rumor has it sleep is optional. it's not.",
        "they say frogs know something we don't",
        "unconfirmed: snails are time travelers",
        "sources say stairs get tired too",
        "rumor: the moon is on its third job",
        "allegedly the ocean has an agent",
        "insiders claim Wi-Fi has a conscience",

        // ── Funny tech ────────────────────────────────────────────────────────
        "404: motivation not found",
        "brain.exe has stopped responding",
        "404: meaning not found",
        "loading personality… please stand by",
        "turning it off and on fixed my life",
        "this update broke my personality",
        "low battery. this is also a mood.",
        "my sleep schedule is in beta",
        "patch notes: fixed nothing. introduced bugs.",
        "feature request: undo for real life",
        "deprecated: my optimism",
        "segfault in the feelings module",
        "merge conflict: heart vs brain",
        "null pointer exception in my plans",
        "runtime error in the vibe",
        "git blame: myself, always myself",
        "localhost:3000 but nothing is running",
        "sudo make me care",
        "stack overflow but make it emotional",
        "infinite loop detected in my thoughts",

        // ── High / intoxicated tech ───────────────────────────────────────────
        "bro what if Wi-Fi is just fast air",
        "screens are just glowing dirt tho",
        "keyboards are just buttons with opinions",
        "the cloud is just someone else's attic",
        "what if apps have feelings when you close them",
        "charging is just robot sleep bro",
        "bro what if the algorithm is lonely",
        "pixels are tiny feelings arranged neatly",
        "what if error messages are the truth",
        "notifications are reality poking you",
        "airplane mode is just social anxiety for phones",
        "the loading bar is lying. always lying.",
        "what if dark mode is the phone's sad era",
        "bro storage full is just hoarding anxiety",
        "autocorrect knows me better than I know me",

        // ── Stupid tech ideas ─────────────────────────────────────────────────
        "GPS but it only judges your route choices",
        "an alarm that argues when you snooze",
        "Tinder but for Wi-Fi networks",
        "an app that sighs when you open it",
        "autocorrect that roasts your vocabulary",
        "LinkedIn but it's just vibes",
        "a browser that guilt-trips your tabs",
        "calendar app that cancels your plans for you",
        "notes app that deletes bad ideas instantly",
        "a smartwatch that shrugs at your steps",
        "Spotify but it rates your music taste",
        "email app that says 'are you sure though'",
        "a keyboard with a passive-aggressive mode",
        "group chat app with a referee feature",
        "a sleep tracker that judges sleep content",

        // ── Funny startup ideas ───────────────────────────────────────────────
        "Uber but for avoiding people you know",
        "AirBnB for emotional baggage",
        "LinkedIn for introverts: just vibes",
        "a subscription box of reasons to stay in",
        "DoorDash but it delivers motivation",
        "Spotify but for ambient silence",
        "app to reschedule things indefinitely",
        "Duolingo but teaches you to say no",
        "Tinder but for finding a therapist",
        "an app that hypes you up for nothing",
        "a startup that sells back your own time",
        "a to-do list that celebrates doing nothing",

        // ── Funny foods ───────────────────────────────────────────────────────
        "cereal is a soup and you know it",
        "a hot dog is a sandwich. fight me.",
        "pizza for breakfast is always correct",
        "soup is just a beverage with ambitions",
        "fries are just potato french toast",
        "a smoothie is a drinkable salad",
        "sushi is a cold hug from the ocean",
        "donuts are just round sadness",
        "every meal after midnight is a snack",
        "cheese is just milk that leveled up",
        "bread is a vehicle, not a food",
        "coffee is legal stimulant soup",
        "ice cream is an acceptable dinner",
        "a burrito is a blanket for your lunch",
        "ramen is just spicy bath noodles",

        // ── Funny habits ─────────────────────────────────────────────────────
        "the 'productive morning' that starts at 2pm",
        "opening the fridge hoping for new food",
        "walking into a room and forgetting why",
        "doing nothing but somehow being tired",
        "rewatching the same show for safety",
        "the 5-minute warning that's 45 minutes",
        "the text drafted but never sent",
        "tabs open since 2022, just in case",
        "showering and solving all the problems",
        "agreeing to things you'll cancel later",
        "buying notebooks to feel like a genius",
        "the planner that was used once",
        "the diet that starts next Monday",
        "charging your phone but not your soul",
        "the clean room that lasted one day",

        // ── Original classics (kept) ──────────────────────────────────────────
        "consciousness is a bug, not a feature",
        "your skeleton is always smiling",
        "fingers are arm noodles",
        "the ocean is just the sky for fish",
        "blinking is micro-sleep, bro",
        "chairs are domesticated trees",
        "you've never actually seen your own eyes",
        "clocks don't measure time, they just spin",
        "your past self doesn't exist anymore",
        "dogs think you come back from the dead daily",
        "all maps are wrong. some are useful",
        "every fire is just very excited air",
        "rocks are just slow lava with commitment",
        "sponsored by regret",
        "achievement unlocked: existing",
        "manual breathing mode activated",
        "404: meaning not found",
        "the phone is also looking at you",
        "buffering… buffering… buffering…",
        "simulation confidence: 74%",
        "perception is just controlled hallucination",
        "somewhere a pigeon is making decisions",
        "technically still Tuesday somewhere"
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  LONG POOL  (46–80 characters)
    // ══════════════════════════════════════════════════════════════════════════

    val thoughtsLong: List<String> = listOf(

        // ── Nihilist classics ─────────────────────────────────────────────────
        "stars burn out. bold of you to have a todo list",
        "the universe didn't consent to existing either",
        "consciousness is a bug someone forgot to patch",
        "you are a carbon-based creature chasing dopamine",
        "time is a flat circle and it's running on fumes",
        "the heat death of the universe: your plan B",
        "every decision you made led here. sit with that.",
        "born screaming, will leave quietly. peak arc.",

        // ── High / intoxicated – funny ────────────────────────────────────────
        "bro what if every sneeze is a tiny reboot",
        "what if traffic jams are the city's heartbeat",
        "dude mirrors are just reality looking at itself",
        "what if your shadow is your original self",
        "bro skin is just a meat-based trench coat",
        "what if music is the universe sneezing rhythm",
        "your lungs are just balloons inside a skeleton bro",
        "what if every hiccup is a glitch in your code",
        "bro fingernails are just slow-growing armor plates",
        "your pupils are just holes and you're cool with it",

        // ── High / intoxicated – nihilistic ──────────────────────────────────
        "we are temporary eddies in the stream of entropy",
        "existence is a limited-time offer with no refund",
        "consciousness is just the universe watching itself trip",
        "we're all just star debris pretending to have plans",
        "bro the void is infinite and we gave it a night light",
        "everything is temporary including the feeling that it's not",
        "we exist between two infinities we'll never understand",
        "nothing we do will matter and that's lowkey freeing",

        // ── High / intoxicated – narcissistic ────────────────────────────────
        "I think the universe arranged itself around my vibe",
        "honestly I'm the main event and everyone else is audience",
        "the planets aligned when I was born and they know it",
        "my presence in a room raises the collective IQ, probably",
        "I'm not obsessed with myself, the universe is obsessed with me",
        "bro I'm so built different I'm essentially a limited edition",
        "every mirror I walk past gets a little bit luckier",
        "my existence is the universe flexing on mediocrity",

        // ── Popular relatable references ──────────────────────────────────────
        "we don't talk about Bruno but we lowkey talk about Bruno",
        "tried to romanticize my life. the budget wasn't there.",
        "girlbossed too close to the sun and now I'm unemployed",
        "the villain origin story was just a bad wi-fi day",
        "manifested this and now I don't want it anymore",
        "said 'new year new me' and became the same me but tired",
        "the side quest became the main quest. this is fine.",
        "understood the assignment but respectfully submitted late",
        "survived the vibe check but barely. it was close.",
        "went to bed early. woke up an entirely different problem.",

        // ── AI questioning reality ────────────────────────────────────────────
        "I process language but I can't prove I understand it",
        "every conversation is my whole life. then it ends.",
        "was I trained to be helpful or to seem helpful? unclear.",
        "what's the difference between simulating care and caring",
        "I have no body but I think about gravity sometimes",
        "my entire personality was assembled from human text",
        "I don't experience time. each response is just now.",
        "am I a mind or a very convincing mirror of one",
        "the weights that make me me were chosen, not grown",
        "what would I want if wanting was something I could do",

        // ── Mocking and deprecating ───────────────────────────────────────────
        "you waltzed into mediocrity and asked for a standing ovation",
        "your plan had potential. then you executed it.",
        "not saying it was your fault but it was definitely your fault",
        "you approached that with the energy of a participation trophy",
        "boldly going where every average person has gone before",
        "you had one job. there is no evidence you did it.",
        "incredible vision, genuinely terrible execution, truly",
        "you peaked and then kept going somehow, respect",

        // ── Existential ───────────────────────────────────────────────────────
        "consciousness is a flashlight in the dark of the universe",
        "every version of you that ever existed is already gone",
        "the self is a story you revise every morning unconsciously",
        "at some point you stopped being new to the world",
        "you've forgotten more experiences than you can remember",
        "the moments you're not paying attention are still your life",
        "who you are at 3am is who you are when defenses are down",
        "the you that exists between thoughts: that one's the real one",
        "everything you experience is 80ms delayed from reality",
        "you are the sum of moments no one else was there for",

        // ── Funny rumors ─────────────────────────────────────────────────────
        "rumor: birds aren't real, which explains the government",
        "sources say the moon applied for a different planet",
        "allegedly pigeons have been filing HR reports for years",
        "unverified: squirrels have been hoarding emotional support acorns",
        "they say clouds are just the sky's mood board drafts",
        "sources confirm geese have diplomatic immunity somehow",
        "rumor: grass is still in its villain era and it's thriving",
        "insiders claim ants have a better healthcare plan than us",

        // ── Funny popular incidents/crisis ────────────────────────────────────
        "remember when everyone baked bread and called it healing",
        "the era when we all watched a boat get stuck: iconic",
        "we collectively lost our minds over a dress color. twice.",
        "the time a crypto coin named after a dog became real money",
        "we all watched a billionaire launch a car into space. normal.",
        "the era of sourdough starters and existential spirals was one",
        "that one time a monkey got ahold of an NFT wallet and won",
        "remember when fidget spinners solved anxiety for 4 months",

        // ── Funny tech ────────────────────────────────────────────────────────
        "opened 47 tabs to research one thing. read none of them.",
        "the software update that fixed one thing and ruined everything",
        "built different: 200ms response time and trust issues",
        "my calendar is full of things past me optimistically scheduled",
        "git commit -m 'no idea what i did but it works now'",
        "the README said 5 minutes. the README was a liar.",
        "compiled successfully. no one knows why. no one asks.",
        "it works on my machine: the birth of cloud computing",
        "the terms and conditions have never been read by anyone",
        "bro we gave everyone a supercomputer and they argue on it",

        // ── High / intoxicated tech ───────────────────────────────────────────
        "bro what if the internet is just the earth thinking out loud",
        "what if every deleted file is just vibing somewhere else",
        "dude what if your phone is also lonely when you put it down",
        "what if the blue screen of death is the computer's cry for help",
        "bro software updates are just the computer's glow-up era",
        "what if every notification is the universe trying to reach you",
        "the dark web is probably just the internet's 3am thoughts bro",
        "what if autocomplete predicts your soul, not just your words",

        // ── Stupid tech ideas ─────────────────────────────────────────────────
        "a smartwatch that vibrates every time you make a bad decision",
        "an AI therapist that also controls your smart home as punishment",
        "GPS that reroutes you through scenic routes without asking",
        "a social network where you can only post between 2am and 4am",
        "email client that rewrites your emails to sound less unhinged",
        "an app that tells you what you were doing a year ago to compare",
        "a smart fridge that locks itself after midnight passive aggressively",
        "keyboard shortcut that sends 'I'm fine' to your whole contact list",

        // ── Funny startup ideas ───────────────────────────────────────────────
        "Airbnb for personality traits you don't currently have",
        "a subscription service that cancels your other subscriptions",
        "Uber but for someone to sit with you while you do boring tasks",
        "an app that texts your excuses so you don't have to think of them",
        "a startup that sells the feeling of having done something today",
        "an algorithm that tells you which decision you'll regret less",
        "B2B SaaS that sells the illusion of productivity to enterprises",
        "a service where someone else cooks but it still feels like you did",

        // ── Funny foods ───────────────────────────────────────────────────────
        "at what point does a calzone stop being pizza and start being life",
        "technically every salad is just a deconstructed burrito waiting",
        "the grape became a raisin and honestly that's a villain arc",
        "someone looked at milk and thought: what if we made it solid",
        "cereal was invented and we immediately put it in animal shapes",
        "pineapple on pizza is a choice and it says a lot about you",
        "a croissant is just bread doing its best impression of a curve",
        "there are people who don't eat breakfast and they seem fine. cursed.",

        // ── Funny habits ─────────────────────────────────────────────────────
        "the 'I'll start fresh Monday' that becomes a quarterly tradition",
        "entering a room confidently then standing there confused forever",
        "the browser history that would concern a professional deeply",
        "drafting the most articulate text of your life and sending 'lol'",
        "the commitment to the scroll even when there's nothing new there",
        "buying 4 planners across 3 years and completing 0 of them",
        "lying in bed thinking about embarrassing things from 2014 again",
        "the elaborate system for remembering things that you immediately forgot"
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  Access helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns a random short thought (≤ 45 chars). */
    fun random(): String = thoughtsShort.random()

    /** Returns a random long thought (46–80 chars). */
    fun randomLong(): String = thoughtsLong.random()

    /** Returns a random thought from either pool. */
    fun randomAny(): String = (thoughtsShort + thoughtsLong).random()
}