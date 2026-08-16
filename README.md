# Modular Machinery Community: Refoxed

## Table of Contents

* [About](#about)
* [Contacts](#contacts)
* [License](#license)
* [Downloads](#downloads)
* [Installation](#installation)
* [Issues](#issues)
* [API](#api)
* [Building](#building)
* [Contribution](#contribution)
* [Localization](#localization)
* [Credits](#credits)

## About

Build your own multi-block machine with easy way and enjoy it with high performance!

Modular Machinery Community Edition on newer Minecraft!

## Contacts

* [Author Email](mailto:dev@howxu.cn)
* [GitHub](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed)

## License

Modular Machinery Community: Refoxed extends the license **[GPL-3.0](./LICENSE)** of [Modular Machinery Community Edition](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition).

For the code refered from [LowDragLib2](https://github.com/Low-Drag-MC/LDLib2), we keep the credits with the license **[LGPL-3.0](https://opensource.org/license/lgpl-3-0)**

[![License](https://img.shields.io/badge/License-GPLv3-red.svg?style=flat-square)](https://opensource.org/license/gpl-3-0) [![License](https://img.shields.io/badge/License-LGPLv3-red.svg?style=flat-square)](https://opensource.org/license/lgpl-3-0)


## Downloads

Downloads can be found on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modular-machinery-community-refoxed), [Modrinth](https://modrinth.com/mod/ae2).

## Installation

Install this mod by putting it into the `minecraft/mods/` folder.
It has no additional hard dependencies.

## Issues

Crashing, Suggestion, Bug?  Create an issue now!

1. Make sure your issue has not already been answered or fixed and you are using the latest version. Also think about whether your issue is a valid one before submitting it.
    * If it is already possible with vanilla and MMCR itself, the suggestion will be considered invalid.
    * Asking for a smaller version, more compact version, or more efficient version of something will also be considered invalid.
2. Go to [the issues page](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/issues) and click [new issue](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/issues/new)
3. If applicable, use one of the provided templates. It will also contain further details about required or useful information to add.
4. Click `Submit New Issue`, and wait for feedback!

Providing as many details as possible does help us to find and resolve the issue faster and also you getting a fixed version as fast as possible.

Please note that we might close any issue not matching these requirements. 

## API

The API for MMCR. It is open source to discuss changes, improve documentation, and provide better add-on support in general.

### Maven

Available on [HowXu's Maven](https://maven.howxu.cn/#/cn/howxu).

You can see snippet on the maven page.

## Building

1. Clone this repository.
2. Build data using the `bash run_data.sh` command. 
3. Build with `./gradlew build`, Jar will be in `build/libs`.
3. For core developer: Load the Gradle project in your IDE

## Contribution

Before you want to add major changes, you might want to discuss them with us first, before wasting your time.

If you are still willing to contribute to this project, you can contribute via [Pull-Request](https://help.github.com/articles/creating-a-pull-request).

The [AE2 Guidelines For Contributing](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/master/.github/CONTRIBUTING.md) contain more detailed information about topics like the used code style and should also be considered.

Here are a few things to keep in mind that will help get your PR approved.

* A PR should be focused on content. Any PRs where the changes are only syntax will be rejected.
* Use the file you are editing as a style guide.
* Consider your feature.
  - Is your suggestion already possible using Vanilla + AE2?
  - Make sure your feature isn't already in the works, or hasn't been rejected previously.
  - Does your feature simplify another feature of AE2? These changes will not be accepted.
  - If your feature can be done by any popular mod, discuss with us first.

**Getting Started**

1. Fork this repository
2. Clone the fork via
  * SSH `git clone git@github.com:<your username>/Applied-Energistics-2.git` or 
  * HTTPS `git clone https://github.com/<your username>/Applied-Energistics-2.git`
3. Change code base
4. Run `gradlew spotlessApply` to apply automatic code formatting
5. Add changes to git `git add -A`
6. Commit changes to your clone `git commit -m "<summary of made changes>"`
7. Push to your fork `git push`
8. Create a Pull-Request on GitHub
9. Wait for review
10. Squash commits for cleaner history

If you are only doing single file pull requests, GitHub supports using a quick way without the need of cloning your fork. Also read up about [synching](https://help.github.com/articles/syncing-a-fork) if you plan to contribute on regular basis.

## Localization

### English Text

`en_US` is included in this repository, fixes to typos are welcome.

### Encoding

Files must be encoded as UTF-8.

### New or updated Translations

We use Crowdin crowd-sourced translations for our localization. You can participate in localizing Applied Energistics 2 on our [Crowdin Page](https://appliedenergistics2.crowdin.com/applied-energistics-2).

Please keep in mind that we use [String format](https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html) to pass additional data to the text for displaying.
Therefore you should preserve parts like `%s` or `%1$d%%`, which allows us to replace them with the correct values while you still have the option to change their order for match the rules of grammar.
This might not be possible for some languages. Should this be the case, please contact us.

### Final Note

If you have issues localizing something, feel free to contact us on [Discord](https://discord.gg/b6HZ4p8EKH).

Thanks to everyone helping out to improve localization of AE2.

## Credits

Thanks to all of our [contributors](https://github.com/AppliedEnergistics/Applied-Energistics-2/graphs/contributors)!
