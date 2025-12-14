New plan. Working on another project allowed me to rethink the way I approach the implementations
in this new "architecture".\
Now I have classes-functions called Actions. Basically they're layouts of how functions (Actions)
that they depend on should be called. E.g. and Action called "Example" should run Action dep1 with
it's input, then pass the result to dep2 and dep3 ran in parallel, and then the result should be
passed to a dep4 run in an update loop.\
All these class functions should be interconnected via some kind of DI which I called Assemblies
in an honor to .NET. Each Action has a corresponding Assembly function. However, Actions are
dependant on other Actions, but Assemblies are allowed to depend on exact implementations and call
other Assemblies. I'm not quite sure if depending of Assemblies on exact Actions is ok, but I'll
leave the final decision to the future self.\
Working a while on that project allowed me to build a diagram showing the logic of that project.
What I want to do now is to migrate that diagram to my own tool and make this tool to generate the
code for me from the diagram. It'll ultimately allow me to develop my "coding" tool while working
on another project.\
The question is where should I start?\
I guess I need to understand the data model of the diagram and/or Actions and Assemblies.
First step will be to focus on the top level function (EntryPoint) and describe it's layout. Then
figure out how could I generate a diagram from it. Then I need a way to edit this diagram, what
should also change the data model. Later I should figure out how to generate Actions and Assemblies
from it.\
Let's start with setting up the project, then describing the function, then rendering it.
I've finished with setting up the project (decided to create a new one to save some time).
Now it is time to describe the function. Where should I start? It has name, input and output
parameters, dependencies and several "layout" functions that describe how dependencies are
interconnected with each other. In the current case the dependencies are called "login", "process
anonymous state" and "process authorised state". They are called sequentially passing their outputs
to the next one. This call sequence is wrapped into "repeat until active" aka infinite loop, which
is wrapped into "retry until result" - a function that keeps looping while the internal one is
throwing exceptions and returning otherwise. The trick is that "repeat until active" returns
Nothing. What makes "retry until result" + "repeat until active" pair to run forever even when
something goes wrong.\
So the data model is:\
-EntryPoint: Action\
--input: infer\
--output: infer\
--body:\
---"retry until result" (input, output)\
----"repeat until active" (input, Nothing)\
-----"login" (input, output1)\
-----"process anonymous state" (output1, output2)\
-----"process authorised state" (output2, output3)"\
The next step is to draw it using a tool that I haven't created yet.

The plan is to define a color theme: I would like it to be neon-like and will ask Gemini to create
one for me. Then I'll try to create data model and render it.

I created the data model. I don't like that ActionLayout.Sequential may have empty list, but I'll
leave it for the future. I've associated ActionDefinitions into a map structure, so it'll be easier
to refer them in the future. I also will add a "current" action: the one the editor should pickup
from the list of actions and strat to render as a root action. Probably I need to add dropdown list
of all the actions to allow a user to choose one.
I think that an ActionDefinition should be a rectangle, it's width should be defined by it's layout
and be a multiple of some value in dp, e.g. 48dp, let's call it a "step". Thus the hight will be
one "step", width will be body plus two (one in the beginning and one in the end) etc.
I'm stuck a bit because of height calculations. It's analysis paralysis. Do I even need to calculate
the height? I think I do. The idea is to represent the diagram as a vertical list of rows, where
each row will draw it's own elements. I also need to know an element's offset at each row. Let's
start simple: I need to draw top-level rectangle with it's name withing. Then I need to draw all
other "layers".
I just realised: I just need to do what I just described - translate an action into "sparse table",
which will contain of "spaces" and rectangles. Should it be a recursive function?

Ok, I did it almost properly. Sequential layout misses internal spaces and I definitely need to add
horizontal scrolling, but it works. So the next steps are to fix these mistakes and add mutability,
because I figured out (again) that immutable data structures are not good for editors, which are
basically just more complex input forms.
I've added horizontal scroll. It is working. I don't like light theme though.
Next: focus on editability (how to add new elements), expandability (how to expand actions) and fix
those spaces.

First step on editability - tap-to-add. When there's nothing on the screen click should add a new
ActionDefinition.
I made everything mutable and realized that probably ActionDefinition fields should be states as
well.
Ok, after fixing couple of bugs I was able to add the first ActionDefinition by click.
The next step is to make the name editable and to add a way to add new functions to the body of
ActionDefinition.

Decided to get rid of input and output types, because I'm not don't anything with them right now.
Also I'll add an id to action, so each ActionDefinition will be able to have a persistent id, while
having a mutable name.
I've added name editing. It is awful now: editing view have internal paddings what makes text
cropped, the font color is grey and look weird, no value checks so you could save an empty value or
the same value as exists, and no save-on-focus-loss. But I'll address it in the future maybe.
Next I need to make it possible to add new functions into an ActionDefinition body.

First of all: the body should be mutable and nullable. This will allow me to not deciding on what
should be there in the beginning and edit it in the future. I'll also fix the error with new
nullable type for ActionDefinition.width: let it be one by default.
I'm stuck: I was "rendering" ActionDefinition hierarchy into a kind of a grid or table that I easily
can draw later, but now, when ActionDefinition.body became a MutableState I realised that I have to
listen to the item in order to redraw it when something changed. This is painful.
I'm thinking if it is possible to traverse through a tree and emit a sequence of "rows". It is
called breadth-first traversal as far as I remember. However, width calculation should be DFS.
Ok, I failed for today. It feels like the solution is near, but I'm tired and cannot proceed. I also
worried that having mutable states without remember() will not have any reasonable effect. Anyway,
the first step is to convert the RenderTable into a sequence of lists using BFS for traversing on
demand.

I have a thought: Compose is saying that it is optimised, so maybe I shouldn't really bother myself
with all these conversion to a table or a sequence and I can just render the table? Yes, it'll be a
repeated hierarchy of rows and columns, but I'll let Compose handle it.
Will force-enable the dark theme, as the light one is burning my eyes.
Oh wow, theme is ignored completely for some reason...
Cool, I managed to add "addition" for ActionDefinition and a button in it's empty body to open a
stub dropdown menu (which is in dark theme, btw). Next need to populate it with meaningful items and
add "addition" logic for all the ActionLayouts types.
I quickly googled the dark theme problem and it appears that I need to wrap everything into
Scaffold. How weird...

Now I can add new "functions" to a ActionDefinition. Added all of them. Not reliable as I manually
iterate over them. Now I need to be able to add RepeatWhileActive (renamed from RepeatUntilActive)
into RetryUntilResult.
Hey, I extracted TextBlock and ActionLayoutSelector, and creation of RetryUntilResult.render was a
piece of cake! Now I'll add RepeatWhileActive and ActionLayout.Action as they should be pretty
straightforward, and then will try to figure out the a to do Sequential.
Completed them and also tweaked some stuff. Time to commit. Had a thought: what if I'll rewrite this
document on each commit. So it'll be possible to read only the thoughts related to the exact commit,
rather than everything at once.
Quick thought: need to add a way to delete Actions, save and load them.
However, next step is Sequential.

Sequential is just a row of items. But how to show that you can add something to it? Well, an "add"
button obviously. Let's do it.
Well it works, kind of. I faced weird bug: when I'm clicking the plus button and adding the second
Action, the first one gets stretched and the second one is squished.
Damn it! I'm using fillMaxWidth() for TextBlocks...
Not sure what to do. Cannot just remove it. Also should prohibit addition of Sequential to
Sequential. Will leave it for tomorrow.

Fixed the problem by explicitly setting Modifier.width(IntrinsicSize.Max) when rendering an item of
Sequential.
Hey, I did it! Can render Sequential properly. Now it'll be nice to save the tree.

Need to fix the bug with the ActionLayout selection dialog not disappearing when selection is made
in Sequential element.
Simple: just explicitly "close" is when an item is selected.

Quick fix: sequential is looking better, I think. The plus button is not that far away from the
other items.
I think I need to convert this to a plugin for Intellij IDEA. It'll be easier to generate/modify
kotlin code using IDE's capabilities.

Need to start with
this https://github.com/JetBrains/intellij-platform-compose-plugin-template/tree/main
The plan is to copy just the essentials from the example to create a stub plugin with a UI. It'll
allow me to work on a plugin instead of a web app. From that point I'll be able to save/load the
results directly to a current project and in kotlin lang.
What is gradle changelog plugin https://github.com/JetBrains/gradle-changelog-plugin? Never saw it
before, maybe will check closely in the future.
The current setup forces me to use `repositories()` withing build.gradle.kts within `:plugin`
module. Want to move it to `settings.gradle.kts` in the future.
There're some gradle properties that are used to build the plugin for different versions/types of
IDEA, will ignore them for now as my target is Android Studio Otter 2025.2.1+. Cannot use it
directly though. Some issue with versioning. But I assume that it should be like IDEA 2025.2.1, so
I'll use it instead.
Ok, I'll actually use gradle properties, as the plugin template pulls all the info from
gradle.properties.
`wrapper` task from the template was not resolving, but it looks like I don't need it: I already
have gradle wrapper.
Adding `plugin.xml`, will need to amend some parameters before release, will leave them as is for
now.
Will avoid `toolWindow#icon` for now.
First of all will add a toolWindow as it is red in `plugin.xml`.
Ok, managed to add `ToolWindowFactory` and now it is green in `plugin.xml`. Next step is to
implement abstract methods of ToolWindowFactory. Will also rename mine to
`VisualIdeToolWindowFactory`.
Need to follow either chatApp or weatherApp examples from the template. Not sure which one to
choose. Let it be a weather app. Both are not working with code, so no difference so far.
It seems like I just need to do `toolWindow.addComposeTab("Name") { ComposeFunction() }` to create a
tab with compose. To do this I need to add `:composeApp` as a dependency. Let see how it turns out.
The `App()` compose function is accessible, but I'm not sure now that it is a right way, as
`:composeApp` is not a library, but rather an app module. Let's run and see...
Yeah, it's failed. Can simply move `App()` to the plugin's module for now.
I'm wondering if I can create a compose multiplatform module containing a UI only.
Added all the same compose dependencies as in `:composeApp`. Synced successfully. Let's run it.
Failed to build. I assume that this is because of empty `CHANGELOG.md`.
Don't see the plugin's window. Is it because of the icon?
Oh, cool! It was because of icon. The panel has appeared, but crashed.
Something with runtime deps
`class androidx.compose.runtime.ComposerImpl cannot be cast to class androidx.compose.runtime.Composer`.
Will try to remove preview and resources.
No result. Will try to remove composeMultiplatform plugin and use material3 dependency directly.
No result. It's weird that Compose and ComposerImpl are from different classloaders... Will remove
material3 to see if it helps. I'm worried that the problem is within my project setup.
Oh, I see, it looks like I'm allowed to use reduced number of elements from
`org.jetbrains.jewel.ui.component`. Have to replace all the material3 components.
Wow. It is awful, but it is working. I can show almost the same ui as it was. So now I have the
plugin core and can work gradually towards generating the code. But first need to fix visual issues:

- cannot move selector in text fields
- `AddNewLayoutSelector` is not working properly: showing both button and dropdown. Need to fix it.
  I can either use just dropdown with an empty state or hide button and show dropdown. I'll probably
  go with the first option, although I don't know how to reuse it on other platforms.
- cannot select "Repeat while active" as it is the first and selected by default option
- "Sequential" looks awful because of `AddNewLayoutSelector`

After fixing these I can start to work on generating code. It was a tough day...
I'm afraid that I'll have to ditch all the other platforms, while doing the plugin. It is a tough,
but right choice for now...

It's a fixing day.
First of all I dealt with TextWithEditor. Simple fix, the TextField just requires to have it's own
state. Want to quickly check if I could add save on loosing focus.
Not a simple thing: I've added Modifier.onFocusChanged() to the TextField that was doing the same
instructions that keyboardActions.onDone() when there's no focus, but it is resetting everything
instantly. I think I'll have to think about it later.
Next is the fix for the AddNewLayoutSelector. As I mentioned I'll remove the call-to-action (CTA)
button and leave just the dropdown. Maybe will change it in the future, but not now. Because of this
decision and of how ListComboBox working I'll have to add an enum containing possible values. Not
the most flexible solution, taking in account that I want to allow users to add custom ActionLayouts
later, but it'll do for now.
Did it. Have a comment: dark theme sucks, it is grey on grey with black outline. Awful.
Also need to figure out a way to hide the dropdown when an item is selected in Sequential.
I didn't, but now I can select the same item each time the dropdown is opened.
Good thing: I just realized that I'd fixed all the remaining plugin's UI issues and can move forward
to code generation.

Ok, it is time to generate some code. I'd talked to Gemini and it helped me to figure out a way to
write files. My plan for today is to add the code suggested by Gemini, add couple of input fields to
edit the generated file location and package, and check if it works.
Added a button to save and it's functionality. Looks ok. Next - logic.
I was about to create a validator/parser, but then realised that I don't care: rubbish in, rubbish
out. Will fix it in the future.
I added the code and its "java.lang.NoClassDefFoundError: org/jetbrains/kotlin/idea/KotlinLanguage".
Trying to understand why.
FUUUUUUUU!!!!!! You have to declare `org.jetbrains.kotlin` in `plugin.xml` in order to "tell" IDEA
that you want to depend on the plugin (assume it will add needed jar to your classloader) and in
`gradle.properties` (actually in `dependencies.intellijiPlatform.plugins` in `build.gradle.kts`) to
have this plugin bundled to your testing IDE. RTFM =( At least it works.
What I have now: can generate code. Want to format it. Next need to propagate functions to
constructor, derive types, figure out if I need and how to decide the return statement. After this
I'll need to figure out how to generate assemblies. Also need to parse functions and assemblies back
to a diagram.
Managed to implement reformatting. Had to spend some time to understand that I need to commit a
Document before reformatting it. Also found out that actions names with spaces are not escaped. Now
I'm thinking that I need to use PSI to generate code.
PSI is hard. Need to understand a lot. Thinking that it would be easier with unit tests to see the
outcomes.
Decided to use backticks always for now.
Had an issue with adding a class to PSI file, but it appears that I forgot to add a newline element.
Ok, I started to get used to it. Not sure that it is intended to be like this, but I'm building PSI
elements, adding to a parent block as text and then parsing to PSI again. Odd.
At least I managed to create my first function in a file with a call to a dependency inside!
I'm tired a bit, but there's some progress and I'm glad about it.

Want to "polish" generation of each ActionLayout and then want to find a way to propagate called
actions upwards to add them as the Action dependencies.
I came up with a simple solution: collect all the Actions in the current ActionDefinition and just
add them to the constructor. Also decided to use fully qualified names. Wondering if I need PSI for
all of these.

Removed part of PSI stuff as it is not needed to generate code.
I'm thinking about dealing with Sequential call: I need a way to pass the input value to the first
function and then to pass results of each function to the next one. I could either add a "variables
counter" to create unique variables all over an Action, or I could just call them in a way that
don't require me to deal with variables at all. This will make the whole call chain a single
expression and I'll not have to deal with variables or return.

I think that I'll need two types for Sequential layout or a flag to distinguish just a sequential
calls from a calls that are passing their results to a next function.

Added arguments passing. Fun to see that the system does exactly what I want, but I don't really
understand what am I doing: decided that I can just fold Sequential and call functions in place
without creating an explicit variables to pass the results to the next function, but it leads to a
part of sequential expression migrates into `repeatWhileActiva` etc. Will not fix it now. Want to do
parsing a code into the diagram first.

Managed to parse classes that are successors to Actions into ActionDefinition structure. Just names,
but it is a good start. For some reason I don't see any UI at all. However I can see that an
ActionDefinition is "selected", thus should be shown.