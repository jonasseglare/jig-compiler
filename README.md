# jig-compiler

A tool that downloads all the Canadian tunes from the [Vitrifolk repository](https://vitrifolk.fr/partitions/partitions-canada.html) and concatenates them into a single PDF while minimizing the number of pages using a binpacking heuristic.

## Usage

`lein run`

## Requirements
The following tools
  * `pdfcrop`
  * `convert`
  * `pdfinfo`
  * `pdfjam`

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
