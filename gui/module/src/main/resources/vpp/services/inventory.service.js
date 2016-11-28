/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
define(['app/vpp/vpp.module', 'next'], function(vpp) {
    vpp.register.factory('VppService', function(VPPRestangular, VPPRestangularXml, VppInterfaceService, $q) {
        var s = {};

        var Vpp = function(name, ipAddress, port, username, password, status) {
            this.name = name || null;
            this.ipAddress = ipAddress || null;
            this.port = port || null;
            this.username = username || null;
            this.password = password || null;
            this.status = status || null;
            this.interfaces = [];
        };

        s.createObj = function(name, ipAddress, port, username, password, status) {
            return new Vpp(name, ipAddress, port, username, password, status);
        };

        s.getVppList = function(successCallback, errorCallback) {
            var vppList = [];
            var promiseList = [];
            var restObj = VPPRestangular.one('restconf').one('operational').one('network-topology:network-topology').one('topology').one('topology-netconf');

            restObj.get().then(function(data) {
                data.topology[0].node.forEach(function(n) {
                    if(n['node-id'] !== 'controller-config') {
                        //create new object
                        var vppObj = s.createObj(n['node-id'], n['netconf-node-topology:host'], n['netconf-node-topology:port'], null, null, n['netconf-node-topology:connection-status']);
                        // register a promise
                        if (vppObj.status === 'connected') {
                            var promise = VppInterfaceService.getInterfaceListByVppName(n['node-id'],
                                function (interfaceList) {
                                    vppObj.interfaces = interfaceList;
                                },
                                function (error) { }
                            );
                            // add promise to array
                            promiseList.push(promise);
                            // when promise is resolved, push vpp into vppList
                            promise.then(
                                function () {
                                    vppList.push(vppObj);
                                },
                                function() {
                                    console.warn('error');
                                }
                            )
                        }
                        else {
                            vppList.push(vppObj);
                        }

                    }
                });

                // when all promises are resolved, call success callback
                $q.all(promiseList).then(function () {
                    successCallback(vppList);
                });
            }, function(res) {
                errorCallback(res);
            });
        };

        s.deleteVpp = function(vpp, finishedSuccessfullyCallback) {
            console.log(vpp);
            var restObj = VPPRestangular
                .one('restconf')
                .one('config')
                .one('network-topology:network-topology')
                .one('topology')
                .one('topology-netconf')
                .one('node')
                .one(vpp.name);

            restObj.remove().then(function() {
                finishedSuccessfullyCallback(true);
            }, function(res) {
                finishedSuccessfullyCallback(false);
            });
        };

        s.mountVpp = function(name, ip, port, un, pw, finishedSuccessfullyCallback) {

            var reqData =  '\
                <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">\
                    <node-id>' + name + '</node-id>\
                    <host xmlns="urn:opendaylight:netconf-node-topology">' + ip + '</host>\
                    <port xmlns="urn:opendaylight:netconf-node-topology">' + port + '</port>\
                    <username xmlns="urn:opendaylight:netconf-node-topology">' + un + '</username>\
                    <password xmlns="urn:opendaylight:netconf-node-topology">' + pw + '</password>\
                    <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>\
                    <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">0</keepalive-delay>\
                </node>';

            var restObj = VPPRestangularXml
                .one('restconf')
                .one('config')
                .one('network-topology:network-topology')
                .one('topology')
                .one('topology-netconf')
                .one('node')
                .one(name);

            restObj.customPUT(reqData).then(function() {
                finishedSuccessfullyCallback(true);
            }, function(res) {
                finishedSuccessfullyCallback(false);
            });
        };

        return s;
    });

    vpp.register.factory('VppInterfaceService', function(VPPRestangular, $q) {
        var s = {};

        s.getInterfaceListByVppName = function(vppName, successCallback, errorCallback) {
            var interfaceList = [];
            var restObj = VPPRestangular.one('restconf').one('operational').one('network-topology:network-topology').one('topology').one('topology-netconf').one('node').one(vppName).one('yang-ext:mount').one('ietf-interfaces:interfaces-state');

            return restObj.get().then(
                function(data) {
                    if (data['interfaces-state'].interface) {
                        interfaceList = data['interfaces-state'].interface.filter(function(i) {
                            if (i.name != 'local0') {
                                return i;
                            }
                        });
                    }
                    successCallback(interfaceList);
                },
                function(res) {
                    errorCallback(res);
                }
            );
        };

        return s;
    });
});
