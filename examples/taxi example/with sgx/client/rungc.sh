JRE_ARGS="\
  -XX:+PrintGCTimeStamps \
  -XX:+PrintGCDateStamps \
  -Xloggc:/app/gcl.log \
  -XX:+PrintGCDetails"
sudo SGXLKL_TAP=sgxlkl_tap0 sgx-lkl-java ./client.img ${JRE_ARGS} -jar /app/processor.jar
