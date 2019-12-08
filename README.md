# jig-compiler

A Clojure library designed to ... well, that part is up to you.

## Usage

FIXME

## Convert commands
Get the size of an image:
```
convert downloads/tune020172_v00.png -format "%w %h" info:
```

Count pages:
https://stackoverflow.com/questions/7462633/count-pages-in-pdf-file-using-imagemagick-php
```
pdfinfo $file | grep Pages: | awk '{print $2}'
```

Image stacking:
```
https://superuser.com/questions/316132/appending-images-vertically-in-imagemagick
```

PDF crop:
```
sudo apt-get install texlive-extra-utils
```
https://askubuntu.com/questions/124692/command-line-tool-to-crop-pdf-files
```
```

PDF-jam
https://www.linuxquestions.org/questions/linux-newbie-8/merge-pdf-files-vertically-and-horizontally-874882/


PDF unite
http://manpages.ubuntu.com/manpages/bionic/man1/pdfunite.1.html



## License

Copyright © 2019 Jonas Östlund

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
