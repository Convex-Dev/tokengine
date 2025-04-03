# README.md

### Ansible playbook for installing tokengine infrastructure

#### Important - this playbook is not complete; do not attempt to use.

This contains Ansible roles for installing Docker, Tokengine and Caddy server (to provide automatic HTTPS termination and certificate handling).

It assumes a knowledge of Ansible and that you have a working Ansible installation on a suitable command machines, typically Linux, and a target machine for Tokengine, which must have an Interent IP address and resolvable URL. The code assumes a machine name of tokengine but this can be changed.

The code has been tested on Ubuntu 22.04 LTS and Ubuntu 24.04 LTS server editions.

Before running this code:

- the URL of the target system must be added to the file host_vars/tokengine/caddy.yml. 

- the target machine IP address must be added to the inventory/hosts file and the target machine should have been configured with an ansible user with passwordless ssh access (e.g. via authorized_keys) and passwordless sudo (e.g. via /etc/sudoers.d/ansible). If you can run 

`ansible <hostname> -i inventory/hosts -m ping`

and get a green result, you should be good to go ahead and run the playbook with:

 `ansible -i inventory/hosts site.yml`
