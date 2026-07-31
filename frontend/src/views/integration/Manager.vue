<template>
<el-card v-loading="loading"><template #header>Integration</template>
<el-alert v-if="error" :title="error" type="error" show-icon/>
<el-form inline><el-input v-model="endpoint.code" placeholder="Code"/><el-input v-model="endpoint.name" placeholder="Name"/><el-input v-model="endpoint.endpoint_url" placeholder="URL"/><el-button :loading="savingEndpoint" @click="addEndpoint">Add endpoint</el-button></el-form>
<el-table :data="endpoints"><el-table-column prop="code" label="Code"/><el-table-column prop="name" label="Name"/><el-table-column prop="endpoint_url" label="URL"/></el-table>
<h3>Subscriptions</h3>
<el-form inline>
 <el-select data-test="subscription-endpoint" v-model="subscription.endpoint_id" placeholder="Endpoint"><el-option v-for="x in endpoints" :key="x.id" :label="x.name||x.code" :value="x.id"/></el-select>
 <el-select v-model="subscription.object_type_id" placeholder="Object type"><el-option v-for="x in objectTypes" :key="x.id" :label="x.name||x.code" :value="x.id"/></el-select>
 <el-select v-model="subscription.event_type"><el-option v-for="x in events" :key="x" :label="x" :value="x"/></el-select>
 <el-button data-test="create-subscription" :loading="savingSubscription" @click="addSubscription">Create subscription</el-button>
</el-form>
<el-table :data="subscriptions"><el-table-column prop="endpoint_id" label="Endpoint"/><el-table-column prop="object_type_id" label="Object type"/><el-table-column prop="event_type" label="Event"/></el-table>
</el-card></template>
<script setup>
import{onMounted,reactive,ref}from'vue';import{ElMessage}from'element-plus';import{listEndpoints,createEndpoint,listSubscriptions,createSubscription}from'../../api/integration';import{listObjectTypes}from'../../api/mdm';
const endpoints=ref([]),subscriptions=ref([]),objectTypes=ref([]),loading=ref(false),savingEndpoint=ref(false),savingSubscription=ref(false),error=ref('');
const endpoint=reactive({code:'',name:'',endpoint_url:'',authentication_type:'NONE'}),subscription=reactive({endpoint_id:null,object_type_id:null,event_type:'RECORD_CHANGED'}),events=['RECORD_CHANGED','RECORD_CREATED','RECORD_UPDATED','RECORD_DELETED'];
async function load(){loading.value=true;error.value='';try{const[e,s,o]=await Promise.all([listEndpoints(),listSubscriptions(),listObjectTypes()]);endpoints.value=e.data||[];subscriptions.value=s.data||[];objectTypes.value=o.data||[]}catch(e){error.value=e?.message||'Unable to load integration settings'}finally{loading.value=false}}
async function addEndpoint(){savingEndpoint.value=true;try{await createEndpoint({...endpoint});await load();ElMessage.success('Endpoint created')}catch(e){error.value=e?.message||'Unable to create endpoint'}finally{savingEndpoint.value=false}}
async function addSubscription(){savingSubscription.value=true;try{await createSubscription({...subscription});subscriptions.value=(await listSubscriptions()).data||[];ElMessage.success('Subscription created')}catch(e){error.value=e?.message||'Unable to create subscription'}finally{savingSubscription.value=false}}
onMounted(load);
</script>