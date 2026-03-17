
Check connection / session state on each proxy instances:


```
for i in $(seq 1 5) ; do
  echo gsren-597-proxy-$i ;
  docker exec -it gsren-597-proxy-$i curl http://localhost:8080/whoami -H'Cookie: JSESSIONID=node0...';
  printf "\n\n";
done
```
