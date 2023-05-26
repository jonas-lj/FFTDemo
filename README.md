# DemoOnline
Note that compiling requires a FRESCO jar containing CRT support. This can be compiled from this [repo](https://github.com/jonas-lj/fresco).

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

Executable jars are as follows:

## fft-online
Tests FFT
```
java -jar fft-online [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)
```

## fixed-mult
Test fixpoint multiplication
```
java -jar fixed-mult [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)
```

## mult
Test normal, integer, multiplication
```
java -jar mult [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)
```

## noise-generation
Tests generation of n41eeded correlated noise
```
java -jar nose-generation [domainInBits] [statSec] [deterrence] [batchSize] [Covert/SemiHonest] [myId] [otherIP1] ([otherIP2] ...)
```

## mascot
Tests offline preprocessing of triples
```
java -jar mascot.jar [domainInBits] [statSec] [batchSize] [SPDZ/CRT] [myId] [otherIP1] ([otherIP2] ...)
```