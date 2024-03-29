# DemoOnline
Note that compiling requires a FRESCO jar containing CRT support. This can be compiled from this [repo](https://github.com/jonas-lj/fresco).
Code is compiled and packed using Maven:
```
mvn package
```

Tests CRT.
In general the parameters are as follows
- `domainInBits`: the amount of bits to be available for computation.
- `statSec`: the statistical security parameter.
- `batchSize`: the size of the batch/underlying coputation.
- `CRT/SPDZ`: a string indicating whether to execute SPDZ or CRT.
- `Covert/SemiHonest`: a string indicating whether to execute covert or semi-honest preprocessing.
- `myId`: ID of the calling party. Must be in 1... parties.
- `otherIP1`: the IP of the first other party.
- `otherIP2`: the *optional* IP of the second other party.
- `otherIPn`: the *optional* IP of the *n*th other party.

Executable jars are as follows and found in the `target` folder after compilation:

## fft-online
Tests FFT
```
java -jar fft-online.jar [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)
```
For example, run the following in two different terminals:
```
java -jar target/fft-online.jar 136 40 1024 CRT 1 localhost
```
and
```
java -jar target/fft-online.jar 136 40 1024 CRT 2 localhost
```

## fixed-mult
Test fixpoint multiplication
```
java -jar fixed-mult.jar [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)
```

## mult
Test normal, integer, multiplication
```
java -jar mult.jar [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)
```

## noise-generation
Tests generation of needed correlated noise
```
java -jar noise-generation.jar [domainInBits] [statSec] [deterrence] [batchSize] [Covert/SemiHonest] [myId] [otherIP1] ([otherIP2] ...)
```

## mascot
Tests offline preprocessing of triples
```
java -jar mascot.jar [domainInBits] [statSec] [batchSize] [SPDZ/CRT] [myId] [otherIP1] ([otherIP2] ...)
```

## BitGen
Tests offline generation of bits needed for SPDZ truncation
```
java -jar SPDZBitGen [domainInBits] [statSec] [batchSize] [myId] [otherIP1] ([otherIP2] ...)
```