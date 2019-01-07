#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export WLS_BASE_IMAGE=store/oracle/weblogic:19.1.0.0
export PRJ_ROOT=../../
export PV_ROOT=/scratch/lihhe/pv

function pullImages() {
  echo "pull docker images"
  docker pull oracle/weblogic-kubernetes-operator:2.0-rc1
  docker tag oracle/weblogic-kubernetes-operator:2.0-rc1 weblogic-kubernetes-operator:2.0
  docker pull traefik:latest
  # TODO: until we has a public site for the image
  docker pull wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0
  docker tag wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0 $WLS_BASE_IMAGE
}

function delImages() {
  docker rmi domain1-image
  docker rmi domain2-image
  docker rmi wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0
  docker rmi $WLS_BASE_IMAGE
  docker rmi traefik:latest
  docker rmi oracle/weblogic-kubernetes-operator:2.0-rc1
  docker rmi weblogic-kubernetes-operator:2.0
}

function createOpt() {
  echo "create namespace test1 to run wls domains"
  kubectl create namespace test1

  echo "install WebLogic operator to namespace weblogic-operator1"
  kubectl create namespace weblogic-operator1
  kubectl create serviceaccount -n weblogic-operator1 sample-weblogic-operator-sa

  helm install $PRJ_ROOT/kubernetes/charts/weblogic-operator \
    --name sample-weblogic-operator \
    --namespace weblogic-operator1 \
    --set serviceAccount=sample-weblogic-operator-sa \
    --set "domainNamespaces={default,test1}" \
    --wait
}

function delOpt() {
  echo "delete operators"
  helm delete --purge sample-weblogic-operator
  kubectl delete namespace weblogic-operator1

  kubectl delete namespace test1
}

function createPV() { 
  if [ ! -e $PV_ROOT/logs ]; then
    mkdir -p $PV_ROOT/logs
    mkdir -p $PV_ROOT/shared
    chmod -R 777 $PV_ROOT/*
  fi

  sed -i 's@%PATH%@'"$PV_ROOT"/logs'@' domain2/pv.yaml
  sed -i 's@%PATH%@'"$PV_ROOT"/shared'@' domain3/pv.yaml 
}

function delPV() {
  rm -rf $PV_ROOT/*
}

function createDomain1() {
  echo "create domain1"
  # create image 'domain1-image' with domainHome in the image
  ./domainHomeBuilder/build.sh domain1 weblogic welcome1

  kubectl -n default create secret generic domain1-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome1

  kubectl create -f domain1/domain1.yaml
}

function createDomain2() {
  echo "create domain2"
  # create image 'domain2-image' with domainHome in the image
  ./domainHomeBuilder/build.sh domain2 weblogic welcome2

  kubectl -n test1 create secret generic domain2-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome2

  kubectl create -f domain2/pv.yaml
  kubectl create -f domain2/pvc.yaml
  kubectl create -f domain2/domain2.yaml
}

function createDomain3() {
  echo "create domain3"
  # generate the domain3 configuration to a host folder
  ./domainHomeBuilder/generate.sh domain3 weblogic welcome3

  kubectl -n test1 create secret generic domain3-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome3

  kubectl create -f domain3/pv.yaml
  kubectl create -f domain3/pvc.yaml
  kubectl create -f domain3/domain3.yaml
}

function createDomains() {
  createDomain1
  createDomain2
  createDomain3

}

function delDomain1() {
  kubectl delete -f domain1/domain1.yaml
  kubectl delete secret domain1-weblogic-credentials
}

function delDomain2() {
  kubectl delete -f domain2/domain2.yaml
  kubectl delete -f domain2/pvc.yaml
  kubectl delete -f domain2/pv.yaml
  kubectl -n test1 delete secret domain2-weblogic-credentials
}

function delDomain3() {
  kubectl delete -f domain3/domain3.yaml
  kubectl delete -f domain3/pvc.yaml
  kubectl delete -f domain3/pv.yaml
  kubectl -n test1 delete secret domain3-weblogic-credentials
}

function delDomains() {
  delDomain1
  delDomain2
  delDomain3
}

function createLB() {
  echo "install Treafik operator to namespace traefik"
  helm install stable/traefik \
    --name traefik-operator \
    --namespace traefik \
    --values $PRJ_ROOT/kubernetes/samples/charts/traefik/values.yaml  \
    --wait

  echo "install Ingress for domains"
  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain1-ingress \
    --set wlsDomain.namespace=default \
    --set wlsDomain.domainUID=domain1 \
    --set traefik.hostname=domain1.org

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain2-ingress \
    --set wlsDomain.namespace=test1 \
    --set wlsDomain.domainUID=domain2 \
    --set traefik.hostname=domain2.org

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain3-ingress \
    --set wlsDomain.namespace=test1 \
    --set wlsDomain.domainUID=domain3 \
    --set traefik.hostname=domain3.org

}

function delLB() {
  echo "delete Ingress"
  helm delete --purge domain1-ingress
  helm delete --purge domain2-ingress
  helm delete --purge domain3-ingress

  echo "delete Traefik operator"
  helm delete --purge traefik-operator
  kubectl delete namespace traefik
}

## Usage: waitDomainReady namespace domainUID
function waitDomainReady() {
  local namespace=$1
  local domainUID=$2
  echo "wait until domain $domainUID is ready"

  # get server number
  serverNum="$(kubectl -n $namespace get domain $domainUID -o=jsonpath='{.spec.replicas}')"
  serverNum=$(expr $serverNum + 1)
  ready=false
  while test $ready != true; do
    if test "$(kubectl -n $namespace get pods  -l weblogic.domainUID=${domainUID},weblogic.createdByOperator=true \
        -o jsonpath='{range .items[*]}{.status.containerStatuses[0].ready}{"\n"}{end}' | wc -l)" != $serverNum; then
      kubectl -n $namespace get pods -l weblogic.domainUID=${domainUID},weblogic.createdByOperator=true
      sleep 5
      continue
    fi
    ready=true
  done
}

## Usage: waitDomainStopped namespace domainUID
function waitDomainStopped() {
  local namespace=$1
  local domainUID=$2
  echo "wait until domain $domainUID stopped"
  while : ; do
    if test "$(kubectl -n $namespace get pods  -l weblogic.domainUID=${domainUID},weblogic.createdByOperator=true \
        -o jsonpath='{range .items[*]}{.status.containerStatuses[0].ready}{"\n"}{end}' | wc -l)" != 0; then
      echo "wait domain shutdown"
      kubectl -n $namespace get pods -l weblogic.domainUID=${domainUID},weblogic.createdByOperator=true
      sleep 5
      continue
    fi
    break
  done
}

function waitDomainsReady() {
  waitDomainReady default domain1
  waitDomainReady test1 domain2
  waitDomainReady test1 domain3
}

function waitDomainsStopped() {
  waitDomainStopped default domain1
  waitDomainStopped test1 domain2
  waitDomainStopped test1 domain3
}

function usage() {
  echo "usage: $0 <cmd>"
  echo "  image cmd: pullImages"
  echo "  This is to pull required images."
  echo
  echo "  operator cmd: createOpt | delOpt"
  echo "  These are to create or delete wls operator."
  echo
  echo "  PV cmd: createPV | delPV"
  echo "  This is to create or delete PV folders and set right host path in the pv yamls."
  echo
  echo "  domains cmd: createDomains | delDomains"
  echo "  These are to create or delete all the demo domains."
  echo
  echo "  one domain cmd: createDomain1 | createDomain2 | createDomain3 | delDomain1 | delDomain2 | delDomain3"
  echo "  These are to create or delete one indivisual domain."
  echo
  echo "  LB cmd: createLB | delLB"
  echo "  These are to create or delete LB operator and Ingress."
  echo
  exit 1
}

function main() {
  if [ "$#" != 1 ] ; then
    usage
  fi
  $1
}

main $@