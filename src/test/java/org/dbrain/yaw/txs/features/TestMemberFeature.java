/*
 * Copyright [2015] [Eric Poitras]
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.dbrain.yaw.txs.features;

import org.dbrain.yaw.app.Configuration;
import org.dbrain.yaw.scope.TransactionScoped;
import org.dbrain.yaw.system.app.BaseQualifiedFeature;
import org.dbrain.yaw.system.txs.TransactionMember;
import org.dbrain.yaw.txs.impl.TestMember;

import javax.inject.Inject;
import java.io.PrintWriter;


/**
 * Created by epoitras on 3/5/15.
 */
public class TestMemberFeature extends BaseQualifiedFeature<TestMemberFeature> {

    private final Configuration config;

    private PrintWriter pw;
    private String      name;
    private boolean failOnFlush  = false;
    private boolean failOncommit = false;


    @Inject
    public TestMemberFeature( Configuration config ) {
        this.config = config;
    }

    @Override
    protected TestMemberFeature self() {
        return this;
    }

    public TestMemberFeature printWriter( PrintWriter pw ) {
        this.pw = pw;
        return self();
    }

    public TestMemberFeature named( String name ) {
        this.name = name;
        return super.named( name );
    }

    public TestMemberFeature failOnFlush() {
        this.failOnFlush = true;
        return self();
    }

    public TestMemberFeature failOnCommit() {
        this.failOncommit = true;
        return self();
    }

    public void commit() {

        config.addService( TestMember.class ) //
                .qualifiedBy( getQualifiers() ) //
                .providedBy( () -> new TestMember( pw, name, failOnFlush, failOncommit ) ) //
                .servicing( TestMember.class ) //
                .servicing( TransactionMember.class ) //
                .in( TransactionScoped.class ) //
                .complete();


    }

}
