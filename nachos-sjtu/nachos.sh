#!/bin/bash
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.AutoGrader -x shCXR.coff
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_files.coff
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_files2.coff -# output=test_files2.out
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_matmult.coff -# output=test_matmult.out
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_proc.coff -# output=test_proc.out
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.UserGrader1 -x grader_user1.coff
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_files3.coff -# output=test_files3.out
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_illegal.coff -# output=test_illegal.out
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc.coff -# output=test_memalloc.out
./timeout3 -t 3 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc2.coff
./timeout3 -t 60 ./nachos.jar -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc3.coff -# output=test_memalloc3.out

